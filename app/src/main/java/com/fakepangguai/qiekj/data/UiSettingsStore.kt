package com.fakepangguai.qiekj.data

import android.content.Context

/**
 * 保存界面偏好（目前只有深色模式）。
 * null 表示用户从未手动设置过，此时回退到系统深色设置。
 */
class UiSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)

    fun readDarkTheme(): Boolean? =
        if (prefs.contains(KEY_DARK_THEME)) prefs.getBoolean(KEY_DARK_THEME, false) else null

    fun saveDarkTheme(darkTheme: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, darkTheme).apply()
    }

    fun readPhone(): String? = prefs.getString(KEY_PHONE, null)

    fun savePhone(phone: String) {
        prefs.edit().putString(KEY_PHONE, phone).apply()
    }

    private companion object {
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_PHONE = "last_phone"
    }
}
