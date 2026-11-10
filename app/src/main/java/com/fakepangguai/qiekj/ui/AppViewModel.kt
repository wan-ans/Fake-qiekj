package com.fakepangguai.qiekj.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fakepangguai.qiekj.data.AppRepository
import com.fakepangguai.qiekj.data.BalanceData
import com.fakepangguai.qiekj.data.DeviceItem
import com.fakepangguai.qiekj.data.OrderHistoryItem
import com.fakepangguai.qiekj.data.ScannedDevice
import com.fakepangguai.qiekj.data.UserInfoData
import com.fakepangguai.qiekj.data.UnauthorizedException
import com.fakepangguai.qiekj.data.UnpaidOrderException
import com.fakepangguai.qiekj.data.WaterSession
import com.fakepangguai.qiekj.data.UnlockResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DeviceTab { Control, Me }

data class DeviceShortcutRequest(
    val goodsId: String?,
    val id: String?,
    val goodsName: String?,
)

data class AppUiState(
    val currentTab: DeviceTab = DeviceTab.Control,
    val hasToken: Boolean = false,
    val phone: String = "",
    val code: String = "",
    val sendingCode: Boolean = false,
    val loggingIn: Boolean = false,
    val loadingDevices: Boolean = false,
    val loadingBalance: Boolean = false,
    val unlocking: Boolean = false,
    val devices: List<DeviceItem> = emptyList(),
    val balance: BalanceData? = null,
    val orderHistory: List<OrderHistoryItem> = emptyList(),
    val unlockStatus: String? = null,
    val orderDetail: UnlockResult? = null,
    val showOrderHistory: Boolean = false,
    val tokenDialogText: String? = null,
    val toastMessage: String? = null,
    val errorMessage: String? = null,
    val darkTheme: Boolean = false,
    val loginSectionExpanded: Boolean = true,
    val scannedDevices: List<ScannedDevice> = emptyList(),
    val scanning: Boolean = false,
    val deviceAliases: Map<String, String> = emptyMap(),
    val endingDeviceId: String? = null,
    val logging: Boolean = false,
    val logLines: Int = 0,
    val unpaidOrder: String? = null,
    val unpaidOrderPrice: String? = null,
    val accountInfo: UserInfoData? = null,
    val miniProgramRequest: String? = null,
)

class AppViewModel(
    private val repository: AppRepository,
    systemDarkTheme: Boolean = false,
) : ViewModel() {
    private var pendingStartDevice: DeviceItem? = null
    private var pendingShortcutRequest: DeviceShortcutRequest? = null
    private val loginSectionInitiallyExpanded = repository.localToken() == null
    private val _state = MutableStateFlow(
        AppUiState(
            hasToken = repository.localToken() != null,
            orderHistory = repository.orderHistory(),
            darkTheme = repository.savedDarkTheme() ?: systemDarkTheme,
            loginSectionExpanded = loginSectionInitiallyExpanded,
            phone = repository.lastPhone().orEmpty(),
            scannedDevices = repository.scannedDevices(),
            deviceAliases = repository.deviceAliases(),
            devices = emptyList(),
            logging = repository.isLogging(),
            logLines = repository.logLineCount(),
        ),
    )
    val state: StateFlow<AppUiState> = _state

    init {
        if (repository.localToken() != null) {
            refreshDevices()
            refreshBalance()
        }
    }

    fun selectTab(tab: DeviceTab) {
        _state.update { it.copy(currentTab = tab) }
        if (tab == DeviceTab.Control && state.value.hasToken && state.value.devices.isEmpty()) {
            refreshDevices()
        }
    }

    fun updatePhone(value: String) {
        _state.update { it.copy(phone = value) }
    }

    fun updateCode(value: String) {
        _state.update { it.copy(code = value) }
    }

    fun scanCode(rawContent: String) = viewModelScope.launch {
        if (rawContent.trim().startsWith(AppRepository.LOGIN_QR_PREFIX)) {
            applyImportedToken(rawContent.trim())
            return@launch
        }
        if (state.value.scanning) return@launch
        registerDevice(rawContent.trim(), "已记录设备：", DeviceItem.SOURCE_SCAN)
    }

    /** 手动输入设备号（SN/IMEI，就是设备行里 SN 后面那串数字）绑定设备。 */
    fun importDeviceCode(code: String) = viewModelScope.launch {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            showError("请输入设备号")
            return@launch
        }
        if (state.value.scanning) return@launch
        registerDevice(trimmed, "已绑定设备：", DeviceItem.SOURCE_CODE)
    }

    private suspend fun registerDevice(rawContent: String, successPrefix: String, source: String) {
        runCatching {
            _state.update { it.copy(scanning = true) }
            repository.scanDevice(rawContent, source)
        }.onSuccess { device ->
            _state.update {
                it.copy(
                    scanning = false,
                    scannedDevices = repository.scannedDevices(),
                    currentTab = DeviceTab.Control,
                )
            }
            showToast(successPrefix + device.toDeviceItem().goodsName)
        }.onFailure {
            _state.update { it.copy(scanning = false) }
            showFailure(it, "识别失败")
        }
    }

    fun toggleLogging() {
        if (repository.isLogging()) {
            repository.log("用户停止记录日志")
            repository.stopLog()
        } else {
            repository.startLog()
            repository.log("用户开始记录日志")
        }
        syncLogState()
    }

    fun clearLog() {
        repository.clearLog()
        syncLogState()
    }

    fun logContent(): String = repository.logContent()

    fun suggestedLogName(): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.CHINA)
            .format(java.util.Date())
        return "qiekj-log-" + stamp + ".txt"
    }

    private fun syncLogState() {
        _state.update {
            it.copy(logging = repository.isLogging(), logLines = repository.logLineCount())
        }
    }
    /** 对未支付订单尝试自动扣款，成功后继续启动。 */
    fun payUnpaidAndStart() = viewModelScope.launch {
        val orderNo = state.value.unpaidOrder ?: return@launch
        val device = pendingStartDevice
        _state.update { it.copy(unpaidOrder = null, unpaidOrderPrice = null) }
        val paid = runCatching { repository.payOrderAutomatically(orderNo) }.getOrDefault(false)
        if (paid) {
            showToast("未支付订单已自动扣款")
            refreshBalance()
            pendingStartDevice = null
            if (device != null) unlock(device)
        } else {
            pendingStartDevice = device
            showToast("自动扣款未成功，请在官方 App 付款后再点「已支付，重试」")
            _state.update { it.copy(unpaidOrder = orderNo) }
        }
    }
    /** 打开胖乖官方小程序付款页（自动扣款失败时的兜底）。 */
    fun openMiniProgramPayment() {
        val orderNo = state.value.unpaidOrder.orEmpty()
        _state.update { it.copy(miniProgramRequest = orderNo) }
    }

    fun consumeMiniProgramRequest() {
        _state.update { it.copy(miniProgramRequest = null) }
    }

    /** 后台守候：定期重试自动扣款，并检测是否已在支付宝付清。 */
    private suspend fun watchOrderPayment(orderNo: String) {
        for (round in 1..120) {
            delay(5000)
            if (repository.isOrderPaid(orderNo)) {
                repository.log("订单 " + orderNo + " 已支付（外部渠道）")
                _state.update { it.copy(unpaidOrder = null, unpaidOrderPrice = null) }
                showToast("订单已支付")
                refreshBalance()
                return
            }
            if (round % 3 == 0) {
                val paid = runCatching { repository.payOrderAutomatically(orderNo) }.getOrDefault(false)
                if (paid) {
                    _state.update { it.copy(unpaidOrder = null, unpaidOrderPrice = null) }
                    showToast("欠单已自动扣款")
                    refreshBalance()
                    return
                }
            }
        }
    }
    /** 用户去官方 App 付清欠单后，回来重试启动。 */
    fun retryAfterUnpaid() = viewModelScope.launch {
        val device = pendingStartDevice
        pendingStartDevice = null
        _state.update { it.copy(unpaidOrder = null, unpaidOrderPrice = null) }
        if (device != null) {
            unlock(device)
        } else {
            showToast("请重新点击设备的「启动」")
        }
    }

    fun dismissUnpaidOrder() {
        pendingStartDevice = null
        _state.update { it.copy(unpaidOrder = null, unpaidOrderPrice = null) }
    }
    fun showAccountInfo() = viewModelScope.launch {
        runCatching { repository.userInfo() }
            .onSuccess { info -> _state.update { it.copy(accountInfo = info) } }
            .onFailure { showFailure(it, "查询账号信息失败") }
    }

    fun dismissAccountInfo() {
        _state.update { it.copy(accountInfo = null) }
    }
    fun exportLoginQr(): String? = repository.loginQrContent()

    fun importToken(rawContent: String) = viewModelScope.launch {
        applyImportedToken(rawContent.trim())
    }

    /** 导入登录态：不再请求短信验证码，直接把 token 写进本地。 */
    private suspend fun applyImportedToken(rawContent: String) {
        val token = rawContent.removePrefix(AppRepository.LOGIN_QR_PREFIX).trim()
        if (token.length < 16) {
            showError("Token 格式不正确")
            return
        }
        runCatching {
            repository.saveToken(token)
        }.onSuccess {
            _state.update {
                it.copy(hasToken = true, loginSectionExpanded = false, code = "")
            }
            showToast("登录态已导入，无需验证码")
            refreshBalance()
            refreshDevices()
        }.onFailure {
            showError(it.message ?: "导入登录失败")
        }
    }

    fun removeScannedDevice(goodsId: String) {
        repository.removeScannedDevice(goodsId)
        _state.update {
            it.copy(
                scannedDevices = repository.scannedDevices(),
                deviceAliases = repository.deviceAliases(),
            )
        }
    }

    fun renameDevice(goodsId: String?, alias: String) {
        if (goodsId.isNullOrBlank()) return
        repository.saveDeviceAlias(goodsId, alias)
        _state.update { it.copy(deviceAliases = repository.deviceAliases()) }
    }

    fun endDevice(device: DeviceItem) = viewModelScope.launch {
        val goodsId = device.goodsId ?: device.id
        if (state.value.endingDeviceId != null) return@launch
        runCatching {
            _state.update { it.copy(endingDeviceId = goodsId, unlockStatus = "正在结束设备") }
            repository.endDeviceSession(device)
        }.onSuccess {
            _state.update { it.copy(endingDeviceId = null, unlockStatus = null) }
            showToast("已结束设备")
            refreshBalance()
        }.onFailure {
            _state.update { it.copy(endingDeviceId = null, unlockStatus = null) }
            showFailure(it, "结束设备失败")
        }
    }
    fun toggleLoginSection() {
        _state.update { it.copy(loginSectionExpanded = !it.loginSectionExpanded) }
    }

    fun setDarkTheme(enabled: Boolean) {
        _state.update { it.copy(darkTheme = enabled) }
        repository.saveDarkTheme(enabled)
    }

    fun sendCode() = viewModelScope.launch {
        val phone = state.value.phone.trim()
        if (phone.isBlank()) {
            showError("请输入手机号")
            return@launch
        }
        runCatching {
            _state.update { it.copy(sendingCode = true) }
            repository.sendCode(phone)
        }.onSuccess {
            showToast("验证码已发送")
        }.onFailure {
            showError(it.message ?: "验证码发送失败")
        }
        _state.update { it.copy(sendingCode = false) }
    }

    fun login() = viewModelScope.launch {
        val phone = state.value.phone.trim()
        val code = state.value.code.trim()
        if (phone.isBlank() || code.isBlank()) {
            showError("请输入手机号和验证码")
            return@launch
        }
        runCatching {
            _state.update { it.copy(loggingIn = true) }
            repository.login(phone, code)
        }.onSuccess {
            repository.saveLastPhone(phone)
            _state.update { it.copy(hasToken = true, loggingIn = false, loginSectionExpanded = false) }
            showToast("登录成功")
            refreshBalance()
            refreshDevices()
        }.onFailure {
            _state.update { it.copy(loggingIn = false) }
            showError(it.message ?: "登录失败")
        }
    }

    fun refreshDevices() = viewModelScope.launch {
        if (!state.value.hasToken) return@launch
        val showLoading = state.value.devices.isEmpty()
        runCatching {
            if (showLoading) {
                _state.update { it.copy(loadingDevices = true) }
            }
            repository.latestDevices()
        }.onSuccess { devices ->
            _state.update { it.copy(devices = devices, loadingDevices = false) }
            consumePendingShortcut(devices)
        }.onFailure {
            _state.update { it.copy(loadingDevices = false) }
            showFailure(it, "查询历史设备失败")
        }
    }

    fun openDeviceShortcut(request: DeviceShortcutRequest) {
        pendingShortcutRequest = request
        _state.update { it.copy(currentTab = DeviceTab.Control) }
        if (!state.value.hasToken) {
            showError("请先登录后再使用桌面设备快捷方式")
            return
        }
        val devices = state.value.devices
        if (devices.isEmpty()) {
            refreshDevices()
        } else {
            consumePendingShortcut(devices)
        }
    }

    private fun consumePendingShortcut(devices: List<DeviceItem>) {
        val request = pendingShortcutRequest ?: return
        val target = devices.firstOrNull { device ->
            (!request.goodsId.isNullOrBlank() && device.goodsId == request.goodsId) ||
                (!request.id.isNullOrBlank() && device.id == request.id) ||
                (!request.goodsName.isNullOrBlank() && device.goodsName == request.goodsName)
        }
        pendingShortcutRequest = null
        if (target == null) {
            showError("未找到对应的历史设备，请刷新设备列表后重试")
            return
        }
        unlock(target)
    }

    fun refreshBalance() = viewModelScope.launch {
        if (!state.value.hasToken) return@launch
        runCatching {
            _state.update { it.copy(loadingBalance = true) }
            repository.queryBalance()
        }.onSuccess { balance ->
            _state.update { it.copy(balance = balance, loadingBalance = false) }
        }.onFailure {
            _state.update { it.copy(loadingBalance = false) }
            showFailure(it, "查询资产失败")
        }
    }

    fun unlock(device: DeviceItem) = viewModelScope.launch {
        if (state.value.unlocking) return@launch
        if (device.categoryCode == SHOWER_CATEGORY) {
            startShowerDevice(device)
            return@launch
        }
        startWaterDevice(device)
    }

    /** 饮水机：下发解锁后立即返回，结算放到后台协程，界面不再长时间卡住。 */
    private suspend fun startWaterDevice(device: DeviceItem) {
        runCatching {
            _state.update { it.copy(unlocking = true, unlockStatus = "准备启动") }
            repository.startWaterDevice(device) { step ->
                _state.update { it.copy(unlockStatus = step) }
                repository.log("步骤：" + step)
            }
        }.onSuccess { session ->
            _state.update { it.copy(unlocking = false, unlockStatus = "已启动，正在等待结束") }
            showToast("设备已启动")
            watchWaterSession(session)
        }.onFailure { error ->
            _state.update { it.copy(unlocking = false, unlockStatus = null) }
            if (error is UnpaidOrderException) {
                pendingStartDevice = device
                repository.log("检测到未支付订单：" + error.orderNo)
                _state.update { it.copy(unpaidOrder = error.orderNo, unpaidOrderPrice = null) }
                val price = repository.orderPayPrice(error.orderNo)
                _state.update {
                    it.copy(unpaidOrderPrice = price, miniProgramRequest = error.orderNo)
                }
                watchOrderPayment(error.orderNo)
            } else {
                showFailure(error, "启动失败")
            }
        }
    }

    private fun watchWaterSession(session: WaterSession) {
        viewModelScope.launch {
            runCatching {
                repository.awaitWaterFinished(session) { step ->
                    _state.update { it.copy(unlockStatus = step) }
                    repository.log("步骤：" + step)
                }
            }.onSuccess { result ->
                _state.update {
                    it.copy(
                        unlockStatus = null,
                        orderDetail = result,
                        orderHistory = repository.orderHistory(),
                    )
                }
                if (result != null) {
                    showToast("用水结束：原价 " + result.originPrice + "，小票 " + result.ticketCost)
                    if (!result.autoPaid) {
                        // 没自动结清就立刻把欠单弹出来，方便马上付款
                        pendingStartDevice = null
                        _state.update {
                            it.copy(
                                unpaidOrder = result.orderNo,
                                unpaidOrderPrice = result.originPrice,
                                miniProgramRequest = result.orderNo,
                            )
                        }
                        watchOrderPayment(result.orderNo)
                    }
                } else {
                    showToast("本次未出水，未产生费用")
                }
                refreshBalance()
            }.onFailure {
                _state.update { it.copy(unlockStatus = null) }
                showFailure(it, "结算失败")
                refreshBalance()
            }
        }
    }


    private suspend fun startShowerDevice(device: DeviceItem) {
        runCatching {
            _state.update { it.copy(unlocking = true, unlockStatus = "准备启动") }
            repository.startShowerDevice(device) { step ->
                _state.update { it.copy(unlockStatus = step) }
                repository.log("步骤：" + step)
            }
        }.onSuccess {
            _state.update { it.copy(unlocking = false, unlockStatus = null) }
            showToast("设备已启动，使用结束后自动结算")
            refreshBalance()
        }.onFailure { error ->
            _state.update { it.copy(unlocking = false, unlockStatus = null) }
            if (error is UnpaidOrderException) {
                pendingStartDevice = device
                repository.log("检测到未支付订单：" + error.orderNo)
                _state.update { it.copy(unpaidOrder = error.orderNo, unpaidOrderPrice = null) }
                val price = repository.orderPayPrice(error.orderNo)
                _state.update {
                    it.copy(unpaidOrderPrice = price, miniProgramRequest = error.orderNo)
                }
                watchOrderPayment(error.orderNo)
            } else {
                showFailure(error, "设备启动失败")
            }
        }
    }

    fun showCurrentToken() {
        val token = repository.localToken()?.takeIf { it.isNotBlank() }
        _state.update { it.copy(tokenDialogText = token ?: "当前未登录，暂无 Token") }
    }

    fun dismissCurrentToken() {
        _state.update { it.copy(tokenDialogText = null) }
    }
    fun consumeToast() {
        _state.update { it.copy(toastMessage = null) }
    }

    fun consumeError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun dismissOrderDetail() {
        _state.update { it.copy(orderDetail = null) }
    }

    fun showOrderHistory() {
        _state.update { it.copy(showOrderHistory = true, orderHistory = repository.orderHistory()) }
    }

    fun dismissOrderHistory() {
        _state.update { it.copy(showOrderHistory = false) }
    }

    fun showHistoricalOrder(item: OrderHistoryItem) {
        _state.update { it.copy(orderDetail = item.toUnlockResult(), showOrderHistory = false) }
    }

    private fun showFailure(error: Throwable, fallback: String) {
        if (error is UnauthorizedException) {
            repository.clearToken()
            _state.update {
                it.copy(
                    hasToken = false,
                    balance = null,
                    devices = emptyList(),
                    loginSectionExpanded = true,
                )
            }
            showError("登录已失效，请重新登录")
            return
        }
        showError(error.message ?: fallback)
    }

    private fun showToast(message: String) {
        repository.log("提示：" + message)
        _state.update { it.copy(toastMessage = message, logLines = repository.logLineCount()) }
    }

    private fun showError(message: String) {
        repository.log("错误：" + message)
        _state.update { it.copy(errorMessage = message, logLines = repository.logLineCount()) }
    }
}

private const val SHOWER_CATEGORY = "08"

class AppViewModelFactory(
    private val repository: AppRepository,
    private val systemDarkTheme: Boolean = false,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppViewModel(repository, systemDarkTheme) as T
    }
}
