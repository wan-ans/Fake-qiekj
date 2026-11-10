package com.fakepangguai.qiekj.data

import android.net.Uri
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit

class AppRepository(
    private val tokenStore: TokenStore,
    private val orderHistoryStore: OrderHistoryStore,
    private val uiSettingsStore: UiSettingsStore,
    private val scannedDeviceStore: ScannedDeviceStore,
    private val deviceAliasStore: DeviceAliasStore,
    private val logStore: LogStore,
) {
    private val api: DeviceApi

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(HeaderInterceptor { tokenStore.readToken() })
            .addInterceptor(ApiLogInterceptor(logStore))
            .addInterceptor(logging)
            .build()
        val moshi = Moshi.Builder()
            .add(SkuResultJsonAdapterFactory())
            .add(EmptyDataJsonAdapter())
            .add(LenientStringJsonAdapter())
            .add(KotlinJsonAdapterFactory())
            .build()

        api = Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DeviceApi::class.java)
    }

    /** 查询订单应付金额（用于未支付订单提示）。 */
    suspend fun orderPayPrice(orderId: String): String? = runCatching {
        api.orderDetail(orderId = orderId, token = requireToken()).data?.payPrice
    }.getOrNull()

    /** 订单是否已支付（orderStatus 非 0 视为已付）。 */
    suspend fun isOrderPaid(orderId: String): Boolean = runCatching {
        val status = api.orderDetail(orderId = orderId, token = requireToken()).data?.orderStatus
        status != null && status != 0
    }.getOrDefault(false)

    fun log(message: String) {
        logStore.append(message)
    }

    fun startLog() {
        logStore.start()
    }

    fun stopLog() {
        logStore.stop()
    }

    fun isLogging(): Boolean = logStore.recording

    fun logContent(): String = logStore.content()

    fun logLineCount(): Int = logStore.lineCount()

    fun clearLog() {
        logStore.clear()
    }

    fun localToken(): String? = tokenStore.readToken()

    fun clearToken() {
        tokenStore.clear()
    }

    /** 直接写入 token（扫码或粘贴导入登录态，不再走短信验证码）。 */
    fun saveToken(token: String) {
        tokenStore.saveToken(token.trim())
    }

    /** 导出登录二维码内容：另一台设备扫码即可导入同一登录态。 */
    fun loginQrContent(): String? =
        tokenStore.readToken()?.takeIf { it.isNotBlank() }?.let { LOGIN_QR_PREFIX + it }

    fun lastPhone(): String? = uiSettingsStore.readPhone()

    fun saveLastPhone(phone: String) {
        uiSettingsStore.savePhone(phone.trim())
    }

    fun orderHistory(): List<OrderHistoryItem> = orderHistoryStore.list()

    fun savedDarkTheme(): Boolean? = uiSettingsStore.readDarkTheme()

    fun scannedDevices(): List<ScannedDevice> = scannedDeviceStore.list()

    fun removeScannedDevice(goodsId: String) {
        scannedDeviceStore.remove(goodsId)
        deviceAliasStore.remove(goodsId)
    }

    fun deviceAliases(): Map<String, String> = deviceAliasStore.all()

    fun saveDeviceAlias(goodsId: String, alias: String) {
        deviceAliasStore.save(goodsId, alias)
    }

    /** 结束设备使用：淋浴走 shower/goods/remoteEnd，其它走 goods/endWater。 */
    suspend fun endDeviceSession(device: DeviceItem) {
        val token = requireToken()
        val goodsId = device.goodsId ?: device.id ?: error("设备缺少 goodsId")
        val response = if (device.categoryCode == "08") {
            api.remoteEndShower(goodsId = goodsId, token = token)
        } else {
            api.endWater(goodsId = goodsId, token = token)
        }
        if (response.code != null && response.code != 0 && response.code != 200) {
            error(response.message ?: response.msg ?: "结束设备失败")
        }
    }

    suspend fun scanDevice(rawContent: String, source: String = DeviceItem.SOURCE_SCAN): ScannedDevice {
        val raw = rawContent.trim()
        require(raw.isNotEmpty()) { "内容不能为空" }
        if (!raw.startsWith("http", ignoreCase = true) && raw.length < 6) {
            error("设备号看起来不完整，请确认后再试")
        }
        val payload = parseScanPayload(raw)
        val token = tokenStore.readToken()?.takeIf { it.isNotBlank() }

        // 二维码里带 SN，也可能直接手输设备号（SN/IMEI），两种都试一遍。
        val candidates = buildList {
            payload.sn?.let { add(it to null) }
            payload.imei?.let { add(null to it) }
            payload.imei?.let { add(it to null) }
        }.ifEmpty { listOf(raw to null) }

        var scan: ScanDeviceData? = null
        var lastMessage: String? = null
        for ((sn, imei) in candidates) {
            val envelope = api.scanDevice(sn = sn, imei = imei, token = token)
            if (envelope.code != null && envelope.code != 0 && envelope.code != 200) {
                lastMessage = envelope.message ?: envelope.msg
                continue
            }
            val data = envelope.data
            if (data?.id != null) {
                scan = data
                break
            }
            lastMessage = envelope.message ?: envelope.msg
        }
        val found = scan ?: error(lastMessage ?: "设备号无法识别")
        val goodsId = found.id ?: error("设备号没有返回设备信息")

        val detail = if (token != null) {
            runCatching { api.getDeviceDetail(goodsId = goodsId, token = token).data }.getOrNull()
        } else {
            null
        }

        val device = ScannedDevice(
            goodsId = goodsId,
            name = detail?.name.orEmpty(),
            sn = payload.sn ?: detail?.imei,
            categoryCode = detail?.categoryCode ?: found.categoryCode,
            categoryName = found.categoryName,
            savedAt = System.currentTimeMillis(),
            source = source,
        )
        scannedDeviceStore.add(device)
        return device
    }

    /**
     * 后付订单自动结算：依次尝试余额、支付宝、微信等通道，
     * 谁能扣成功就用谁（H5 对应 pay/unify/prePay + pay/unify/pay，payType：2=余额 1=支付宝 3=微信 51/62=银联类）。
     * 返回 true 表示订单已结清。
     */
    suspend fun payOrderAutomatically(orderNo: String): Boolean {
        val token = requireToken()
        if (orderNo.isBlank()) return false
        val payTypes = listOf("2", "1", "3", "51", "62")
        for (payType in payTypes) {
            runCatching { api.prePay(orderNo = orderNo, payType = payType, token = token) }
            val result = runCatching {
                api.payWithBalance(orderNo = orderNo, payType = payType, token = token)
            }.getOrNull()
            val ok = result != null && (result.code == 0 || result.code == 200)
            if (ok) {
                logStore.append("订单 " + orderNo + " 已自动扣款成功，payType=" + payType)
                return true
            }
            val reason = result?.let { it.message ?: it.msg } ?: "无响应"
            logStore.append("订单 " + orderNo + " 自动扣款失败 payType=" + payType + " -> " + reason)
        }
        return false
    }

    /**
     * 淋浴（categoryCode = 08）设备扫码即启动、用完结算，接口与饮水机不同：
     * skus -> goods/shower/unlock -> machine/msg/sync 轮询。
     */
    suspend fun startShowerDevice(
        device: DeviceItem,
        onStep: suspend (String) -> Unit,
    ) {
        val token = requireToken()
        val goodsId = device.goodsId ?: device.id ?: error("设备缺少 goodsId")

        onStep("正在检查设备状态")
        val detail = api.getDeviceDetail(goodsId = goodsId, token = token).requireData()
        detail.deviceErrorCode?.takeIf { it.isNotBlank() }?.let {
            error(detail.deviceErrorMsg ?: "设备异常【\$it】")
        }
        if (detail.soldState != null && detail.soldState != 1) {
            error("设备当前不可用（状态 \${detail.soldState}）")
        }

        onStep("正在获取出水口")
        val skuEnvelope = api.goodsid2sku(goodsId = goodsId, token = token)
        if (skuEnvelope.code == UNPAID_ORDER_CODE) {
            throw UnpaidOrderException(skuEnvelope.data?.incompleteOrderNo.orEmpty())
        }
        skuEnvelope.throwIfFailed()
        val skuId = skuEnvelope.data?.skus?.firstOrNull()?.skuId
            ?: error(skuEnvelope.message ?: skuEnvelope.msg ?: "设备暂不可用，未获取到出水口")

        onStep("正在下发启动指令")
        val msgId = api.unlockShower(skuId = skuId, token = token).requireData().msgId
            ?: error("启动指令未返回 msgId")

        onStep("等待设备响应")
        for (attempt in 1..15) {
            delay(1000)
            val message = runCatching { api.syncMachineMsg(msgId = msgId, token = token).data }.getOrNull()
                ?: continue
            if (message.state == "OK" || message.code == "05") return
            val stateText = message.state ?: message.code ?: "未知状态"
            error("设备启动失败：" + stateText)
        }
        error("设备未响应启动指令，请稍后重试")
    }

    private fun parseScanPayload(raw: String): ScanPayload {
        if (!raw.startsWith("http", ignoreCase = true)) {
            return ScanPayload(sn = raw, imei = null)
        }
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
            ?: return ScanPayload(sn = null, imei = null)
        val sn = uri.getQueryParameter("SN")?.takeIf { it.isNotBlank() }
        val imei = uri.getQueryParameter("IMEI")?.takeIf { it.isNotBlank() }
        return ScanPayload(sn = sn, imei = imei)
    }

    private data class ScanPayload(val sn: String?, val imei: String?)

    fun saveDarkTheme(darkTheme: Boolean) {
        uiSettingsStore.saveDarkTheme(darkTheme)
    }

    suspend fun sendCode(phone: String) {
        api.sendCode(phone = phone).throwIfFailed()
    }

    suspend fun login(phone: String, code: String): String {
        val token = api.login(phone = phone, verify = code).requireData().token
            ?: error("登录成功但未返回 token")
        tokenStore.saveToken(token)
        return token
    }

    suspend fun queryBalance(shopId: String? = null): BalanceData {
        val token = requireToken()
        return api.queryBalance(shopId = shopId, token = token).requireData()
    }

    suspend fun userInfo(): UserInfoData {
        val token = requireToken()
        return api.userInfo(token).requireData()
    }

    suspend fun latestDevices(): List<DeviceItem> {
        val token = requireToken()
        val envelope = api.getLatestUsed(token = token)
        envelope.throwIfFailed()
        return envelope.data.orEmpty()
    }

    /** 饮水机启动：只下发解锁指令，立即返回，不阻塞界面。 */
    suspend fun startWaterDevice(
        device: DeviceItem,
        onStep: suspend (String) -> Unit,
    ): WaterSession {
        val token = requireToken()
        val goodsId = device.goodsId ?: device.id ?: error("设备缺少 goodsId")

        onStep("正在获取 SKU")
        val skuEnvelope = api.goodsid2sku(goodsId = goodsId, token = token)
        if (skuEnvelope.code == UNPAID_ORDER_CODE) {
            throw UnpaidOrderException(skuEnvelope.data?.incompleteOrderNo.orEmpty())
        }
        skuEnvelope.throwIfFailed()
        val skuId = skuEnvelope.data?.skus?.firstOrNull()?.skuId
            ?: error(skuEnvelope.message ?: skuEnvelope.msg ?: "设备暂不可用，未获取到出水口")

        onStep("正在获取 IMEI")
        val imei = api.getDeviceDetail(goodsId = goodsId, token = token).requireData().imei
            ?: error("未获取到 imei")

        onStep("正在检查积分")
        api.useIntergral(token).throwIfFailed()

        onStep("正在开通后付")
        api.addUserAfterPayChannel(token = token).throwIfFailed()

        onStep("正在检查位置风控")
        api.isCheckLocation(imei = imei, token = token).throwIfFailed()

        onStep("正在启动解锁")
        // 与官方 H5 一致：不传 promotions，只传 forbiddenPromotionTypes，
        // 让服务端自动把小票/优惠应用到订单上，否则会留下小额未支付订单（code 2128）。
        val unlock = api.unlockWater(
            skuId = skuId,
            forbiddenPromotionTypes = "",
            token = token,
        ).requireData()

        val orderNo = unlock.orderNo ?: error("未获取到订单号")
        return WaterSession(device = device, skuId = skuId, orderNo = orderNo)
    }

    /** 后台等待本次用水结束，结束后生成后付订单并返回账单。 */
    suspend fun awaitWaterFinished(
        session: WaterSession,
        onStep: suspend (String) -> Unit,
    ): UnlockResult? {
        val token = requireToken()
        var finalOrderNo = session.orderNo
        var completed = false
        for (attempt in 1..MAX_SYNC_ATTEMPTS) {
            delay(1000)
            val status = runCatching {
                api.syncWater(skuId = session.skuId, token = token).requireData()
            }.getOrNull()
            if (status != null) {
                finalOrderNo = status.identify ?: finalOrderNo
                if (status.workStatus != 2) {
                    completed = true
                    break
                }
            }
            onStep("设备工作中，正在等待完成")
        }
        if (!completed) {
            onStep("未收到结束状态，尝试直接结算")
        }

        onStep("正在创建后付订单")
        val createEnvelope = runCatching { api.createAfterPay(orderNo = finalOrderNo, token = token) }.getOrNull()
            ?: error("创建订单请求失败，请稍后到「历史订单」确认")
        if (createEnvelope.code != null && createEnvelope.code != 0 && createEnvelope.code != 200) {
            error("本次用水未生成账单：" + (createEnvelope.message ?: createEnvelope.msg ?: "未知原因"))
        }
        // 设备端没有人按“出水”，或出水量为 0 时，不会有后付订单，属于正常情况。
        val orderId = createEnvelope.data?.orderId ?: return null

        // 订单建好后立刻尝试自动结算，避免留下未支付订单把下次启动卡住（服务端 code 2128）。
        val paid = runCatching { payOrderAutomatically(finalOrderNo) }.getOrDefault(false)
        if (!paid) {
            logStore.append("订单 " + finalOrderNo + " 未自动结清，需要手动支付")
        }

        onStep("正在查询订单详情")
        val detail = runCatching {
            api.orderDetail(orderId = orderId, token = token).requireData()
        }.getOrNull()
        val ticketCost = detail?.promotionList?.firstOrNull { it.promotionType == 4 }?.discountAmount ?: "-"
        val integralCost = detail?.promotionList?.firstOrNull { it.promotionType == 8 }?.discountAmount ?: "-"
        val otherPromotions = detail?.promotionList
            ?.filter { it.promotionType != 4 && it.promotionType != 8 }
            ?.map {
                PromotionSummary(
                    promotionType = it.promotionType,
                    discountAmount = it.discountAmount,
                )
            }.orEmpty()

        val result = UnlockResult(
            autoPaid = paid,
            orderNo = finalOrderNo,
            orderId = orderId,
            originPrice = detail?.tradeOrderItem?.firstOrNull()?.originPrice ?: "-",
            ticketCost = ticketCost,
            integralCost = integralCost,
            otherPromotions = otherPromotions,
            completedAt = System.currentTimeMillis(),
        )
        orderHistoryStore.add(
            OrderHistoryItem(
                orderNo = result.orderNo,
                orderId = result.orderId,
                goodsName = session.device.goodsName.ifBlank { "未命名设备" },
                originPrice = result.originPrice,
                ticketCost = result.ticketCost,
                integralCost = result.integralCost,
                otherPromotions = result.otherPromotions,
                completedAt = result.completedAt,
            ),
        )
        return result
    }

    private fun requireToken(): String = tokenStore.readToken()?.takeIf { it.isNotBlank() }
        ?: error("请先登录")

    private fun ApiEnvelope<*>.throwIfFailed() {
        if (code != null && code != 0 && code != 200) {
            error(message ?: msg ?: "请求失败")
        }
    }

    companion object {
        const val LOGIN_QR_PREFIX = "qiekj-login:1|"

        private const val MAX_SYNC_ATTEMPTS = 900
        private const val UNPAID_ORDER_CODE = 2128

    }
}
