package com.fakepangguai.qiekj.data

import com.squareup.moshi.Json

data class ApiEnvelope<T>(
    val code: Int? = null,
    val msg: String? = null,
    val message: String? = null,
    val data: T? = null,
) {
    fun requireData(): T {
        if (code == 2) throw UnauthorizedException(message ?: msg ?: "登录已失效")
        return data ?: error(message ?: msg ?: "接口未返回 data")
    }
}

/** 服务端返回“未登录”时抛出，用于清理本地 token 并引导重新登录。 */
class UnauthorizedException(message: String) : IllegalStateException(message)

data class EmptyData(
    val ignored: String? = null,
)

data class LoginData(
    val token: String? = null,
)

data class DeviceItem(
    val goodsId: String? = null,
    val goodsName: String = "",
    val id: String? = null,
    val categoryCode: String? = null,
    val sn: String? = null,
    val scanned: Boolean = false,
    val source: String = SOURCE_SCAN,
) {
    companion object {
        const val SOURCE_SCAN = "scan"
        const val SOURCE_CODE = "code"
    }
}

data class UserInfoData(
    val phone: String? = null,
    val registerTime: String? = null,
    val balance: String? = null,
    val realName: String? = null,
    val campusName: String? = null,
)

data class BalanceData(
    val balance: String? = null,
    val tokenCoin: String? = null,
    @Json(name = "integral") val integral: String? = null,
    val integralAmount: String? = null,
) {
    val ticketText: String
        get() = tokenCoin?.toDoubleOrNull()?.let { "%.2f".format(it / 100.0) } ?: "-"

    val pointsText: String get() = integral ?: "-"
}

data class SkuData(val skuId: String? = null)

data class SkuResult(
    val skus: List<SkuData> = emptyList(),
    val incompleteOrderNo: String? = null,
    val incompleteOrderId: String? = null,
)

/** 设备被未支付订单卡住时抛出，UI 引导用户支付后重试。 */
class UnpaidOrderException(
    val orderNo: String,
    message: String = "有未支付订单需要完成",
) : IllegalStateException(message)

data class DeviceDetailData(
    val goodsId: String? = null,
    val name: String? = null,
    val categoryCode: String? = null,
    val orgName: String? = null,
    val payment: String? = null,
    val imei: String? = null,
    val soldState: Int? = null,
    val deviceErrorCode: String? = null,
    val deviceErrorMsg: String? = null,
)

data class WaterSession(
    val device: DeviceItem,
    val skuId: String,
    val orderNo: String,
)

data class ScanDeviceData(
    val id: String? = null,
    val shopId: String? = null,
    val categoryCode: String? = null,
    val categoryName: String? = null,
    val type: String? = null,
)

data class SimpleApiResponse(
    val code: Int? = null,
    val msg: String? = null,
    val message: String? = null,
)

data class PrePayData(
    val prepayParam: String? = null,
    val gateway: String? = null,
    val payNo: String? = null,
    val exist: Boolean? = null,
)

data class MsgIdData(
    val msgId: String? = null,
)

data class MachineMsgData(
    val state: String? = null,
    val code: String? = null,
)

data class ScannedDevice(
    val goodsId: String,
    val name: String = "",
    val sn: String? = null,
    val categoryCode: String? = null,
    val categoryName: String? = null,
    val savedAt: Long = 0L,
    val source: String = DeviceItem.SOURCE_SCAN,
) {
    fun toDeviceItem(): DeviceItem = DeviceItem(
        goodsId = goodsId,
        goodsName = name.ifBlank { categoryName ?: goodsId },
        categoryCode = categoryCode,
        sn = sn,
        scanned = true,
        source = source,
    )
}

data class UnlockData(
    val msgId: String? = null,
    val orderNo: String? = null,
)

data class SyncData(
    val workStatus: Int? = null,
    val identify: String? = null,
)

data class AfterPayCreatingData(
    val orderId: String? = null,
)

data class OrderDetailData(
    val tradeOrderItem: List<TradeOrderItem> = emptyList(),
    val promotionList: List<PromotionItem> = emptyList(),
    val payPrice: String? = null,
    val orderStatus: Int? = null,
    val tokenCoinDiscount: String? = null,
)

data class TradeOrderItem(
    val originPrice: String? = null,
)

data class PromotionItem(
    val promotionType: Int? = null,
    val discountAmount: String? = null,
)

data class UnlockResult(
    val autoPaid: Boolean = true,
    val orderNo: String,
    val orderId: String,
    val originPrice: String,
    val ticketCost: String,
    val integralCost: String,
    val otherPromotions: List<PromotionSummary>,
    val completedAt: Long,
)

data class PromotionSummary(
    val promotionType: Int?,
    val discountAmount: String?,
)

data class OrderHistoryItem(
    val orderNo: String,
    val orderId: String,
    val goodsName: String,
    val originPrice: String,
    val ticketCost: String,
    val integralCost: String,
    val otherPromotions: List<PromotionSummary>,
    val completedAt: Long,
) {
    fun toUnlockResult(): UnlockResult = UnlockResult(
        orderNo = orderNo,
        orderId = orderId,
        originPrice = originPrice,
        ticketCost = ticketCost,
        integralCost = integralCost,
        otherPromotions = otherPromotions,
        completedAt = completedAt,
    )
}
