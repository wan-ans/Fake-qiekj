package com.fakepangguai.qiekj.data

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface DeviceApi {
    @FormUrlEncoded
    @POST("common/sms/sendCode")
    suspend fun sendCode(
        @Field("phone") phone: String,
        @Field("template") template: String = "reg",
    ): ApiEnvelope<EmptyData>

    @FormUrlEncoded
    @POST("user/reg")
    suspend fun login(
        @Field("channel") channel: String = ApiConfig.LOGIN_CHANNEL,
        @Field("phone") phone: String,
        @Field("verify") verify: String,
    ): ApiEnvelope<LoginData>

    @FormUrlEncoded
    @POST("user/balance")
    suspend fun queryBalance(
        @Field("shopId") shopId: String? = null,
        @Field("token") token: String,
    ): ApiEnvelope<BalanceData>

    @FormUrlEncoded
    @POST("user/info")
    suspend fun userInfo(@Field("token") token: String): ApiEnvelope<UserInfoData>

    @FormUrlEncoded
    @POST("goods/latestUsed")
    suspend fun getLatestUsed(
        @Field("categoryCode") categoryCode: String = "5",
        @Field("token") token: String,
    ): ApiEnvelope<List<DeviceItem>>

    @FormUrlEncoded
    @POST("goods/normal/skus")
    suspend fun goodsid2sku(
        @Field("goodsId") goodsId: String,
        @Field("token") token: String,
    ): ApiEnvelope<SkuResult>

    @FormUrlEncoded
    @POST("pay/unify/prePay")
    suspend fun prePay(
        @Field("orderNo") orderNo: String,
        @Field("payType") payType: String = "2",
        @Field("token") token: String,
    ): ApiEnvelope<PrePayData>

    @FormUrlEncoded
    @POST("pay/unify/pay")
    suspend fun payWithBalance(
        @Field("orderNo") orderNo: String,
        @Field("payType") payType: String = "2",
        @Field("token") token: String,
    ): SimpleApiResponse

    @FormUrlEncoded
    @POST("goods/normal/details")
    suspend fun getDeviceDetail(
        @Field("goodsId") goodsId: String,
        @Field("token") token: String,
    ): ApiEnvelope<DeviceDetailData>

    @FormUrlEncoded
    @POST("goods/scan/v2")
    suspend fun scanDevice(
        @Field("SN") sn: String? = null,
        @Field("IMEI") imei: String? = null,
        @Field("token") token: String? = null,
    ): ApiEnvelope<ScanDeviceData>

    @FormUrlEncoded
    @POST("goods/shower/unlock")
    suspend fun unlockShower(
        @Field("skuId") skuId: String,
        @Field("forbiddenPromotionTypes") forbiddenPromotionTypes: String = "",
        @Field("token") token: String,
    ): ApiEnvelope<MsgIdData>

    @FormUrlEncoded
    @POST("shower/goods/remoteEnd")
    suspend fun remoteEndShower(
        @Field("goodsId") goodsId: String,
        @Field("token") token: String,
    ): SimpleApiResponse

    @FormUrlEncoded
    @POST("goods/endWater")
    suspend fun endWater(
        @Field("goodsId") goodsId: String,
        @Field("token") token: String,
    ): SimpleApiResponse

    @FormUrlEncoded
    @POST("machine/msg/sync")
    suspend fun syncMachineMsg(
        @Field("msgId") msgId: String,
        @Field("token") token: String,
    ): ApiEnvelope<MachineMsgData>

    @FormUrlEncoded
    @POST("userIntegral/checkUserIsRisk")
    suspend fun useIntergral(@Field("token") token: String): ApiEnvelope<EmptyData>

    @FormUrlEncoded
    @POST("payChannelRoute/addUserAfterPayChannel")
    suspend fun addUserAfterPayChannel(
        @Field("method") method: String = "15",
        @Field("token") token: String,
    ): ApiEnvelope<EmptyData>

    @FormUrlEncoded
    @POST("orderRisk/isCheckLocation")
    suspend fun isCheckLocation(
        @Field("categoryCode") categoryCode: String = "04",
        @Field("imei") imei: String,
        @Field("token") token: String,
    ): ApiEnvelope<EmptyData>

    @FormUrlEncoded
    @POST("goods/water/unlock")
    suspend fun unlockWater(
        @Field("skuId") skuId: String,
        @Field("forbiddenPromotionTypes") forbiddenPromotionTypes: String = "",
        @Field("token") token: String,
    ): ApiEnvelope<UnlockData>

    @FormUrlEncoded
    @POST("goods/water/sync")
    suspend fun syncWater(
        @Field("skuId") skuId: String,
        @Field("token") token: String,
    ): ApiEnvelope<SyncData>

    @FormUrlEncoded
    @POST("order/afterPay/creating")
    suspend fun createAfterPay(
        @Field("orderNo") orderNo: String,
        @Field("token") token: String,
    ): ApiEnvelope<AfterPayCreatingData>

    @FormUrlEncoded
    @POST("order/detail")
    suspend fun orderDetail(
        @Field("orderId") orderId: String,
        @Field("token") token: String,
    ): ApiEnvelope<OrderDetailData>
}
