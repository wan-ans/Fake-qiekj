package com.fakepangguai.qiekj.data

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import java.lang.reflect.Type

/**
 * goods/normal/skus 的 data 有两种形状：
 *  - 数组：[{skuId:...}]                     正常可用
 *  - 对象：{incompleteOrderNo,incompleteOrderId}  有未支付订单（code 2128）
 * 这里统一收口成 SkuResult，避免 "Expected BEGIN_ARRAY but was BEGIN_OBJECT"。
 */
class SkuResultJsonAdapterFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (annotations.isNotEmpty() || type != SkuResult::class.java) return null
        val elementAdapter = moshi.adapter(SkuData::class.java)

        return object : JsonAdapter<SkuResult>() {
            override fun fromJson(reader: JsonReader): SkuResult {
                return when (reader.peek()) {
                    JsonReader.Token.BEGIN_ARRAY -> {
                        val skus = mutableListOf<SkuData>()
                        reader.beginArray()
                        while (reader.hasNext()) {
                            elementAdapter.fromJson(reader)?.let { skus.add(it) }
                        }
                        reader.endArray()
                        SkuResult(skus = skus)
                    }
                    JsonReader.Token.BEGIN_OBJECT -> {
                        var orderNo: String? = null
                        var orderId: String? = null
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "incompleteOrderNo" -> orderNo = readString(reader)
                                "incompleteOrderId" -> orderId = readString(reader)
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        SkuResult(incompleteOrderNo = orderNo, incompleteOrderId = orderId)
                    }
                    else -> {
                        reader.skipValue()
                        SkuResult()
                    }
                }
            }

            override fun toJson(writer: JsonWriter, value: SkuResult?) {
                writer.beginArray()
                value?.skus?.forEach { elementAdapter.toJson(writer, it) }
                writer.endArray()
            }

            private fun readString(reader: JsonReader): String? = when (reader.peek()) {
                JsonReader.Token.NULL -> reader.nextNull()
                JsonReader.Token.NUMBER, JsonReader.Token.STRING -> reader.nextString()
                else -> {
                    reader.skipValue()
                    null
                }
            }
        }
    }
}
