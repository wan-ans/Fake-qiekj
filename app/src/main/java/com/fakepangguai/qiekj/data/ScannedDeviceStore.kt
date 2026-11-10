package com.fakepangguai.qiekj.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/** 通过扫码记录下来的设备，下次可直接启动，不需要再扫。 */
class ScannedDeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("scanned_devices", Context.MODE_PRIVATE)
    private val adapter = Moshi.Builder()
        .add(LenientStringJsonAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<ScannedDevice>>(
            Types.newParameterizedType(List::class.java, ScannedDevice::class.java),
        )

    fun list(): List<ScannedDevice> {
        val raw = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        return runCatching { adapter.fromJson(raw).orEmpty() }.getOrDefault(emptyList())
    }

    fun add(device: ScannedDevice) {
        val next = (list().filterNot { it.goodsId == device.goodsId } + device)
            .sortedByDescending { it.savedAt }
            .take(MAX_DEVICES)
        prefs.edit().putString(KEY_DEVICES, adapter.toJson(next)).apply()
    }

    fun remove(goodsId: String) {
        val next = list().filterNot { it.goodsId == goodsId }
        prefs.edit().putString(KEY_DEVICES, adapter.toJson(next)).apply()
    }

    private companion object {
        const val KEY_DEVICES = "devices"
        const val MAX_DEVICES = 50
    }
}
