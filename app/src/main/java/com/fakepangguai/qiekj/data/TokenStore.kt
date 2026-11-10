package com.fakepangguai.qiekj.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_token",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    // EncryptedSharedPreferences 每次读取都会做一次解密（约 5~15ms），
    // 而 HeaderInterceptor 每个请求都要读 token，这里做内存缓存。
    @Volatile
    private var cachedToken: String? = null
    @Volatile
    private var cacheLoaded = false

    fun readToken(): String? {
        if (cacheLoaded) return cachedToken
        synchronized(this) {
            if (!cacheLoaded) {
                cachedToken = prefs.getString(KEY_TOKEN, null)
                cacheLoaded = true
            }
            return cachedToken
        }
    }

    fun saveToken(token: String) {
        synchronized(this) {
            cachedToken = token
            cacheLoaded = true
        }
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun clear() {
        synchronized(this) {
            cachedToken = null
            cacheLoaded = true
        }
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
    }
}
