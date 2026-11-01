package com.m3browser.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Frosted glass over a WebView. Haze and parent RenderEffects do not see WebView pixels
 * (they live in the WebView's own drawing), so on Android 12+ this copies the WebView
 * region behind the bar with PixelCopy and blurs that bitmap. Older versions, or a
 * failed copy, use a translucent tint only.
 */
@Composable
fun Frosted(
    enabled: Boolean,
    webView: WebView?,
    tint: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var region by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()
    val host = LocalView.current
    DisposableEffect(enabled, webView) {
        if (!enabled || webView == null || Build.VERSION.SDK_INT < 31) {
            frame = null
            return@DisposableEffect onDispose { }
        }
        val job = scope.launch {
            while (isActive) {
                val rect = region
                val wv = webView
                if (rect != null && wv != null && host.isAttachedToWindow) {
                    captureWebView(wv, rect)?.let { frame = it }
                }
                delay(140)
            }
        }
        onDispose { job.cancel() }
    }
    Box(
        modifier
            .onGloballyPositioned { region = it.boundsInWindow() }
            .clip(shape),
    ) {
        val shot = frame
        if (enabled && shot != null && Build.VERSION.SDK_INT >= 31) {
            Image(
                shot, null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    renderEffect = androidx.compose.ui.graphics.BlurEffect(18f, 18f, androidx.compose.ui.graphics.TileMode.Clamp)
                    clip = true
                },
                contentScale = ContentScale.Crop,
            )
        }
        Box(Modifier.fillMaxSize().background(tint))
        content()
    }
}

private suspend fun captureWebView(webView: WebView, rectInWindow: androidx.compose.ui.geometry.Rect): ImageBitmap? {
    if (webView.width <= 0 || webView.height <= 0) return null
    val loc = IntArray(2)
    webView.getLocationInWindow(loc)
    val left = (rectInWindow.left - loc[0]).toInt()
    val top = (rectInWindow.top - loc[1]).toInt()
    val width = rectInWindow.width.toInt().coerceAtLeast(2)
    val height = rectInWindow.height.toInt().coerceAtLeast(2)
    // Draw the WebView itself so the copy is the page, not the bar drawn on top of it.
    val drawn = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(drawn)
    canvas.translate(-left.toFloat(), -top.toFloat())
    val drew = runCatching { webView.draw(canvas) }.isSuccess
    if (drew && !isFlat(drawn)) return drawn.asImageBitmap()
    drawn.recycle()
    // Fallback: copy the window strip just beside the bar (page pixels, not the bar).
    val activity = webView.context as? Activity ?: return null
    val sample = android.graphics.Rect(
        rectInWindow.left.toInt().coerceAtLeast(0),
        (rectInWindow.top - rectInWindow.height).toInt().coerceAtLeast(0),
        rectInWindow.right.toInt(),
        rectInWindow.top.toInt().coerceAtLeast(1),
    )
    if (sample.width() < 2 || sample.height() < 2) return null
    val bitmap = Bitmap.createBitmap(sample.width(), sample.height(), Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { cont ->
        try {
            PixelCopy.request(activity.window, sample, bitmap, { result ->
                if (result == PixelCopy.SUCCESS && !isFlat(bitmap)) cont.resume(bitmap.asImageBitmap())
                else {
                    bitmap.recycle()
                    cont.resume(null)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            bitmap.recycle()
            if (cont.isActive) cont.resume(null)
        }
    }
}

private fun isFlat(bitmap: Bitmap): Boolean {
    if (bitmap.width < 2 || bitmap.height < 2) return true
    val a = bitmap.getPixel(1, 1)
    val b = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
    val c = bitmap.getPixel(bitmap.width - 2, bitmap.height - 2)
    return a == b && b == c && (a ushr 24) < 8
}
