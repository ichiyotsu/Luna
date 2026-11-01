package com.m3browser

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** GitHub token lives in encrypted prefs backed by the Android Keystore. Never synced. */
class TokenStore(context: Context) {
    private val prefs: SharedPreferences = create(context.applicationContext)

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var username: String?
        get() = prefs.getString(KEY_USER, null)
        set(value) = prefs.edit().putString(KEY_USER, value).apply()

    var avatarUrl: String?
        get() = prefs.getString(KEY_AVATAR, null)
        set(value) = prefs.edit().putString(KEY_AVATAR, value).apply()

    var gistId: String?
        get() = prefs.getString(KEY_GIST, null)
        set(value) = prefs.edit().putString(KEY_GIST, value).apply()

    var lastSync: Long
        get() = prefs.getLong(KEY_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_SYNC, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun create(context: Context): SharedPreferences {
        return try {
            val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context,
                "m3_github",
                master,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            context.getSharedPreferences("m3_github_fallback", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_USER = "user"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_GIST = "gist"
        private const val KEY_SYNC = "sync"
    }
}
