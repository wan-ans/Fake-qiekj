package com.fakepangguai.qiekj.data

import android.content.Context

/** 设备备注名：按 goodsId 存一份别名，用于区分同一台/不同台设备。 */
class DeviceAliasStore(context: Context) {
    private val prefs = context.getSharedPreferences("device_alias", Context.MODE_PRIVATE)

    fun all(): Map<String, String> = prefs.all
        .mapNotNull { (key, value) -> (value as? String)?.takeIf { it.isNotBlank() }?.let { key to it } }
        .toMap()

    fun save(goodsId: String, alias: String) {
        val trimmed = alias.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(goodsId).apply()
        } else {
            prefs.edit().putString(goodsId, trimmed).apply()
        }
    }

    fun remove(goodsId: String) {
        prefs.edit().remove(goodsId).apply()
    }
}
