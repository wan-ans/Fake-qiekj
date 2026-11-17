package com.fakepangguai.qiekj.data

import android.content.Context

/**
 * 本地已删除（隐藏）的设备集合：按 goodsId/id 记录。
 *
 * 历史设备来自服务端，无法真正在远端删除；如果只从扫码记录里删掉，
 * 服务端返回的历史设备会把同一台设备重新塞回列表（并且排到最下面）。
 * 所以删除时把设备 key 记到这里，合并列表时统一过滤掉。
 */
class HiddenDeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("hidden_devices", Context.MODE_PRIVATE)

    fun all(): Set<String> = prefs.getStringSet(KEY_DEVICES, emptySet()).orEmpty()

    fun add(goodsId: String) {
        if (goodsId.isBlank()) return
        prefs.edit().putStringSet(KEY_DEVICES, all() + goodsId).apply()
    }

    fun remove(goodsId: String) {
        if (goodsId.isBlank()) return
        prefs.edit().putStringSet(KEY_DEVICES, all() - goodsId).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_DEVICES).apply()
    }

    private companion object {
        const val KEY_DEVICES = "devices"
    }
}
