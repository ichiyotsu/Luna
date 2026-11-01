package com.m3browser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.m3browser.BrowserController
import com.m3browser.BrowserTab
import com.m3browser.PageScripts
import com.m3browser.TranslateHost
import com.m3browser.languageDiffers
import com.m3browser.languageLabel
import com.m3browser.primaryLang
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

data class TranslatePrompt(val tabId: Long, val host: String, val pageLang: String)

class TranslateController(
    private val browser: BrowserController,
    private val scope: CoroutineScope,
    private val onMessage: (String) -> Unit,
) : TranslateHost {
    var targetLang: String = "zh"
    var never: Set<String> = emptySet()
    var onNever: (String) -> Unit = {}
    var prompt by mutableStateOf<TranslatePrompt?>(null)
    var busy by mutableStateOf(false)
    private val dismissed = HashSet<String>()
    private val clients = HashMap<String, Translator>()
    private var activeSource: String = TranslateLanguage.ENGLISH

    fun dismiss() { prompt = null }

    fun neverSite() {
        val host = prompt?.host ?: return
        prompt = null
        dismissed.add(host)
        onNever(host)
    }

    fun acceptPrompt() {
        val tab = browser.tabs.firstOrNull { it.id == prompt?.tabId } ?: browser.current
        prompt = null
        if (tab != null) translate(tab)
    }

    fun translate(tab: BrowserTab) {
        if (tab.isHome || tab.webView == null) {
            onMessage("当前没有可翻译的网页")
            return
        }
        scope.launch {
            busy = true
            val target = mlCode(targetLang)
            if (target == null) {
                busy = false
                onMessage("暂不支持翻译为${languageLabel(targetLang)}")
                return@launch
            }
            val source = mlCode(activeSource) ?: TranslateLanguage.ENGLISH
            if (source == target) {
                busy = false
                onMessage("页面已是${languageLabel(targetLang)}")
                return@launch
            }
            val ok = runCatching { ensureModel(source, target) }.isSuccess
            busy = false
            if (!ok) {
                onMessage("语言模型下载失败。设备需要能访问 Google 的模型服务，可稍后重试。")
                return@launch
            }
            tab.translated = true
            browser.runPageJs(tab, PageScripts.startTranslate())
        }
    }

    fun showOriginal(tab: BrowserTab) {
        tab.translated = false
        browser.runPageJs(tab, PageScripts.stopTranslate())
    }

    override fun onLanguage(tabId: Long, lang: String, sample: String) {
        scope.launch {
            val tab = browser.tabs.firstOrNull { it.id == tabId } ?: return@launch
            if (tab.translated || tab.isHome) return@launch
            var detected = lang
            if (detected.isBlank() && sample.isNotBlank()) detected = identify(sample).orEmpty()
            if (detected.isNotBlank()) activeSource = primaryLang(detected)
            val host = com.m3browser.UrlUtils.displayHost(tab.url)
            if (host.isBlank() || host in never || host in dismissed) return@launch
            if (!languageDiffers(detected, targetLang)) return@launch
            if (prompt?.tabId == tabId) return@launch
            prompt = TranslatePrompt(tabId, host, detected)
        }
    }

    override fun onBatch(tabId: Long, json: String) {
        val tab = browser.tabs.firstOrNull { it.id == tabId } ?: return
        if (!tab.translated) return
        scope.launch {
            val arr = runCatching { JSONArray(json) }.getOrNull() ?: return@launch
            val target = mlCode(targetLang) ?: return@launch
            val source = mlCode(activeSource) ?: TranslateLanguage.ENGLISH
            if (source == target) return@launch
            val translator = runCatching { ensureModel(source, target) }.getOrNull() ?: return@launch
            val out = JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val text = item.optString("t")
                val translated = runCatching { translateText(translator, text) }.getOrDefault(text)
                out.put(JSONObject().put("id", item.optString("id")).put("t", translated))
            }
            browser.runPageJs(tab, PageScripts.applyTranslations(out.toString()))
        }
    }

    private fun mlCode(tag: String): String? {
        val primary = primaryLang(tag)
        return TranslateLanguage.fromLanguageTag(primary)
            ?: TranslateLanguage.fromLanguageTag(tag)
    }

    private suspend fun ensureModel(source: String, target: String): Translator {
        val key = "$source>$target"
        clients[key]?.let { return it }
        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build(),
        )
        suspendCancellableCoroutine { cont ->
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
        }
        clients[key] = translator
        return translator
    }

    private suspend fun translateText(translator: Translator, text: String): String =
        suspendCancellableCoroutine { cont ->
            translator.translate(text)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(text) }
        }

    private suspend fun identify(sample: String): String? = suspendCancellableCoroutine { cont ->
        LanguageIdentification.getClient().identifyLanguage(sample)
            .addOnSuccessListener { lang -> cont.resume(if (lang == "und") null else lang) }
            .addOnFailureListener { cont.resume(null) }
    }
}
