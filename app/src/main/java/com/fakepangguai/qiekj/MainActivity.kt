package com.fakepangguai.qiekj

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fakepangguai.qiekj.data.AppRepository
import com.fakepangguai.qiekj.data.DeviceItem
import com.fakepangguai.qiekj.data.OrderHistoryItem
import com.fakepangguai.qiekj.data.OrderHistoryStore
import com.fakepangguai.qiekj.data.DeviceAliasStore
import com.fakepangguai.qiekj.data.LogStore
import com.fakepangguai.qiekj.data.ScannedDevice
import com.fakepangguai.qiekj.data.ScannedDeviceStore
import com.fakepangguai.qiekj.data.TokenStore
import com.fakepangguai.qiekj.data.UiSettingsStore
import com.fakepangguai.qiekj.data.UnlockResult
import com.fakepangguai.qiekj.ui.AppViewModel
import com.fakepangguai.qiekj.ui.AppViewModelFactory
import com.fakepangguai.qiekj.ui.DeviceShortcutRequest
import com.fakepangguai.qiekj.ui.DeviceTab
import com.fakepangguai.qiekj.ui.theme.DeviceControlTheme
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

private const val ACTION_OPEN_DEVICE_SHORTCUT = "com.fakepangguai.qiekj.OPEN_DEVICE_SHORTCUT"
private const val PROJECT_REPOSITORY_URL = "https://github.com/wan-ans/Fake-qiekj"
private const val EXTRA_GOODS_ID = "goods_id"
private const val EXTRA_DEVICE_ID = "device_id"
private const val EXTRA_GOODS_NAME = "goods_name"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val repository = remember {
                AppRepository(
                    tokenStore = TokenStore(applicationContext),
                    orderHistoryStore = OrderHistoryStore(applicationContext),
                    uiSettingsStore = UiSettingsStore(applicationContext),
                    scannedDeviceStore = ScannedDeviceStore(applicationContext),
                    deviceAliasStore = DeviceAliasStore(applicationContext),
                    logStore = LogStore(),
                )
            }
            val systemDarkTheme = isSystemInDarkTheme()
            val vm: AppViewModel = viewModel(
                factory = AppViewModelFactory(repository, systemDarkTheme),
            )
            val uiState by vm.state.collectAsState()
            DeviceControlTheme(darkTheme = uiState.darkTheme) {
                ApplySystemBars(darkTheme = uiState.darkTheme)
                LaunchedEffect(vm) {
                    shortcutRequestFromIntent(intent)?.let(vm::openDeviceShortcut)
                }
                DeviceControlApp(vm)
            }
        }
    }
}

private fun shortcutRequestFromIntent(intent: Intent?): DeviceShortcutRequest? {
    if (intent?.action != ACTION_OPEN_DEVICE_SHORTCUT) return null
    return DeviceShortcutRequest(
        goodsId = intent.getStringExtra(EXTRA_GOODS_ID),
        id = intent.getStringExtra(EXTRA_DEVICE_ID),
        goodsName = intent.getStringExtra(EXTRA_GOODS_NAME),
    )
}

private fun pinDeviceShortcut(context: Context, device: DeviceItem) {
    val shortcutManager = context.getSystemService(ShortcutManager::class.java)
    if (shortcutManager?.isRequestPinShortcutSupported != true) {
        Toast.makeText(context, "当前桌面不支持添加快捷方式", Toast.LENGTH_LONG).show()
        return
    }

    val label = device.goodsName.ifBlank { "历史设备" }
    val shortcutIntent = Intent(context, MainActivity::class.java).apply {
        action = ACTION_OPEN_DEVICE_SHORTCUT
        putExtra(EXTRA_GOODS_ID, device.goodsId)
        putExtra(EXTRA_DEVICE_ID, device.id)
        putExtra(EXTRA_GOODS_NAME, device.goodsName)
    }
    val shortcut = ShortcutInfo.Builder(
        context,
        "device-${device.goodsId ?: device.id ?: device.goodsName.hashCode()}",
    )
        .setShortLabel(label.take(10))
        .setLongLabel(label)
        .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
        .setIntent(shortcutIntent)
        .build()

    shortcutManager.requestPinShortcut(shortcut, null)
}

/**
 * 唤起胖乖官方支付宝小程序付款页。
 * appId=2018072460764274 是胖乖生活小程序（H5 源码里 alipays:// 跳转用的就是这个 appId）。
 * 依次尝试：付款页 -> 订单列表 -> 小程序首页 -> 分享链接。
 */
private fun openMiniProgramPayment(context: Context, orderNo: String) {
    val appId = "2018072460764274"
    val encodedQuery = Uri.encode(if (orderNo.isBlank()) "" else "orderNo=" + orderNo)
    val links = buildList {
        if (orderNo.isNotBlank()) {
            add("alipays://platformapi/startapp?appId=" + appId +
                "&page=pages/pay/paymentOrder/paymentOrder&query=" + encodedQuery)
            add("alipays://platformapi/startapp?appId=" + appId +
                "&page=pages%2Fpay%2FpaymentOrder%2FpaymentOrder&query=" + encodedQuery)
        }
        add("alipays://platformapi/startapp?appId=" + appId + "&page=pages/order/orderList/orderList")
        add("alipays://platformapi/startapp?appId=" + appId)
    }
    for (link in links) {
        val launch = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(launch) }.isSuccess) return
    }
    Toast.makeText(context, "没有找到支付宝小程序，请手动打开支付宝搜索「胖乖生活」", Toast.LENGTH_LONG).show()
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

@Composable
private fun ApplySystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val barColor = (if (darkTheme) Color(0xFF141414) else Color(0xFFFAFAF8)).toArgb()
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
        @Suppress("DEPRECATION")
        window.statusBarColor = barColor
        @Suppress("DEPRECATION")
        window.navigationBarColor = barColor
        window.setBackgroundDrawable(ColorDrawable(barColor))
    }
}

private fun maskedPhone(phone: String): String {
    val trimmed = phone.trim()
    return when {
        trimmed.isEmpty() -> "已登录"
        trimmed.length >= 7 -> trimmed.take(3) + "****" + trimmed.takeLast(4)
        else -> trimmed
    }
}

@Composable
private fun DeviceControlApp(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.toastMessage) {
        val message = state.toastMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        vm.consumeToast()
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        vm.consumeError()
    }

    LaunchedEffect(state.miniProgramRequest) {
        val orderNo = state.miniProgramRequest ?: return@LaunchedEffect
        openMiniProgramPayment(context, orderNo)
        vm.consumeMiniProgramRequest()
    }

    state.tokenDialogText?.let { token ->
        TokenDialog(token = token, onDismiss = vm::dismissCurrentToken)
    }

    if (state.showOrderHistory) {
        OrderHistoryDialog(
            orders = state.orderHistory,
            onDismiss = vm::dismissOrderHistory,
            onOpenOrder = vm::showHistoricalOrder,
        )
    }

    state.orderDetail?.let { detail ->
        OrderDetailDialog(
            detail = detail,
            onDismiss = vm::dismissOrderDetail,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = state.currentTab == DeviceTab.Control,
                    onClick = { vm.selectTab(DeviceTab.Control) },
                    icon = { androidx.compose.material3.Icon(Icons.Outlined.Home, contentDescription = null) },
                    label = { Text("设备控制") },
                )
                NavigationBarItem(
                    selected = state.currentTab == DeviceTab.Me,
                    onClick = { vm.selectTab(DeviceTab.Me) },
                    icon = { androidx.compose.material3.Icon(Icons.Outlined.Person, contentDescription = null) },
                    label = { Text("我的") },
                )
            }
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (state.currentTab) {
                DeviceTab.Control -> ControlScreen(state, vm)
                DeviceTab.Me -> MeScreen(state, vm)
            }
        }
    }
}


@Composable
private fun TokenDialog(token: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("当前 Token") },
        text = {
            Text(
                text = token,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        shape = RoundedCornerShape(8.dp),
    )
}
@Composable
private fun OrderDetailDialog(
    detail: UnlockResult,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("订单详情") },
        text = {
            Column {
                DetailLine("订单号", detail.orderNo)
                DetailLine("订单 ID", detail.orderId)
                DetailLine("订单原价", detail.originPrice)
                DetailLine("花费小票", detail.ticketCost)
                DetailLine("积分抵扣", detail.integralCost)
                if (detail.otherPromotions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("其他优惠", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    detail.otherPromotions.forEach { promotion ->
                        DetailLine(
                            label = "类型 ${promotion.promotionType ?: "-"}",
                            value = promotion.discountAmount ?: "-",
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        },
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}


@Composable
private fun ControlScreen(
    state: com.fakepangguai.qiekj.ui.AppUiState,
    vm: AppViewModel,
) {
    val context = LocalContext.current
    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val contents = result.data?.getStringExtra(QrScannerActivity.EXTRA_CONTENTS)
        if (!contents.isNullOrBlank()) {
            vm.scanCode(contents)
        }
    }
    val devices = remember(state.scannedDevices, state.devices) {
        mergeDevices(state.scannedDevices, state.devices)
    }
    var menuDevice by remember { mutableStateOf<com.fakepangguai.qiekj.data.DeviceItem?>(null) }
    var renameTarget by remember { mutableStateOf<com.fakepangguai.qiekj.data.DeviceItem?>(null) }
    var showDeviceCode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                PageTitle(
                    title = "设备控制",
                    subtitle = state.unlockStatus ?: when {
                        !state.hasToken -> "请先到“我的”页面登录"
                        devices.isEmpty() -> "还没有设备，扫码添加"
                        else -> "共 " + devices.size + " 台设备"
                    },
                )
            }
            OutlinedButton(
                onClick = { showDeviceCode = true },
                enabled = !state.scanning,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text("设备号")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    scannerLauncher.launch(Intent(context, QrScannerActivity::class.java))
                },
                enabled = !state.scanning,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                androidx.compose.material3.Icon(
                    Icons.Outlined.QrCodeScanner,
                    contentDescription = null,
                )
                Spacer(Modifier.padding(horizontal = 3.dp))
                Text(if (state.scanning) "识别中" else "扫码")
            }
        }
        Spacer(Modifier.height(18.dp))

        if (state.loadingDevices) {
            LoadingText("正在查询历史设备")
        } else if (devices.isEmpty()) {
            EmptyText("点右上角「扫码」或「设备号」添加设备")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 18.dp),
            ) {
                items(devices, key = { it.goodsId ?: it.id ?: it.goodsName }) { device ->
                    val key = device.goodsId ?: device.id
                    DeviceRow(
                        name = displayName(device, state.deviceAliases),
                        subtitle = deviceSubtitle(device, state.deviceAliases),
                        canStart = !state.unlocking && state.endingDeviceId == null,
                        ending = state.endingDeviceId != null && state.endingDeviceId == key,
                        onStart = { vm.unlock(device) },
                        onEnd = { vm.endDevice(device) },
                        onOpenMenu = { menuDevice = device },
                    )
                }
            }
        }
    }

    menuDevice?.let { device ->
        DeviceMenuDialog(
            title = displayName(device, state.deviceAliases),
            canDelete = device.scanned,
            onDismiss = { menuDevice = null },
            onRename = {
                renameTarget = device
                menuDevice = null
            },
            onAddShortcut = {
                pinDeviceShortcut(context, device.copy(goodsName = displayName(device, state.deviceAliases)))
                menuDevice = null
            },
            onCopyCode = {
                val code = device.sn?.takeIf { it.isNotBlank() }
                    ?: device.goodsId?.takeIf { it.isNotBlank() }
                    ?: device.id
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (code != null && clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("device-code", code))
                    Toast.makeText(context, "设备号已复制：" + code, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "这台设备没有可复制的设备号", Toast.LENGTH_SHORT).show()
                }
                menuDevice = null
            },
            onDelete = {
                device.goodsId?.let { vm.removeScannedDevice(it) }
                menuDevice = null
            },
        )
    }

    renameTarget?.let { device ->
        RenameDeviceDialog(
            defaultName = device.goodsName.ifBlank { "未命名设备" },
            currentAlias = (device.goodsId ?: device.id)?.let { state.deviceAliases[it] }.orEmpty(),
            onDismiss = { renameTarget = null },
            onConfirm = { alias ->
                vm.renameDevice(device.goodsId ?: device.id, alias)
                renameTarget = null
            },
        )
    }

    if (showDeviceCode) {
        DeviceCodeDialog(
            onDismiss = { showDeviceCode = false },
            onConfirm = { code ->
                vm.importDeviceCode(code)
                showDeviceCode = false
            },
        )
    }

    state.unpaidOrder?.let { orderNo ->
        AlertDialog(
            onDismissRequest = vm::dismissUnpaidOrder,
            title = { Text(if (orderNo.isBlank()) "欠费提示" else "有未支付订单") },
            text = {
                Column {
                    if (orderNo.isNotBlank()) {
                        Text("订单号：" + orderNo)
                    }
                    state.unpaidOrderPrice?.let { price ->
                        Spacer(Modifier.height(6.dp))
                        Text("应付金额：¥" + price)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("这笔订单要先付清才能继续用设备。自动扣款失败时会自动唤起胖乖小程序，也可以点下面「打开小程序付款」；付完回来会自动识别。")
                    Spacer(Modifier.height(10.dp))
                    if (orderNo.isNotBlank()) {
                        Text(
                            text = "点这里试「自动扣款」（仅余额通道可用）",
                            modifier = Modifier.clickable(onClick = vm::payUnpaidAndStart),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = vm::openMiniProgramPayment) {
                    Text("打开小程序付款")
                }
            },
            dismissButton = {
                TextButton(onClick = vm::retryAfterUnpaid) {
                    Text("已支付，重试启动")
                }
            },
            shape = RoundedCornerShape(8.dp),
        )
    }
}

private fun mergeDevices(
    scannedDevices: List<ScannedDevice>,
    historyDevices: List<com.fakepangguai.qiekj.data.DeviceItem>,
): List<com.fakepangguai.qiekj.data.DeviceItem> {
    val scannedItems = scannedDevices.map { it.toDeviceItem() }
    val scannedIds = scannedItems.mapNotNull { it.goodsId }.toSet()
    return scannedItems + historyDevices.filterNot { (it.goodsId ?: it.id) in scannedIds }
}

private fun displayName(
    device: com.fakepangguai.qiekj.data.DeviceItem,
    aliases: Map<String, String>,
): String {
    val alias = (device.goodsId ?: device.id)?.let { aliases[it] }?.takeIf { it.isNotBlank() }
    return alias ?: device.goodsName.ifBlank { "未命名设备" }
}

private fun deviceSubtitle(
    device: com.fakepangguai.qiekj.data.DeviceItem,
    aliases: Map<String, String>,
): String? {
    val alias = (device.goodsId ?: device.id)?.let { aliases[it] }?.takeIf { it.isNotBlank() }
    val parts = mutableListOf<String>()
    if (alias != null) parts.add(device.goodsName.ifBlank { "未命名设备" })
    if (device.scanned) {
        parts.add(if (device.source == DeviceItem.SOURCE_CODE) "设备号绑定" else "扫码记录")
    }
    when (device.categoryCode) {
        "08" -> parts.add("淋浴")
        "04" -> parts.add("饮水机")
        "05" -> parts.add("充电桩")
    }
    device.sn?.takeIf { it.isNotBlank() }?.let { parts.add("SN " + it) }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeScreen(
    state: com.fakepangguai.qiekj.ui.AppUiState,
    vm: AppViewModel,
) {
    val context = LocalContext.current
    var showLoginQr by remember { mutableStateOf(false) }
    var showImportToken by remember { mutableStateOf(false) }
    val exportLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(vm.logContent().toByteArray())
                } ?: error("无法写入文件")
            }.isSuccess
            Toast.makeText(context, if (ok) "日志已导出" else "导出失败", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        PageTitle("我的", if (state.hasToken) "已登录" else "未登录")
        Spacer(Modifier.height(22.dp))

        if (state.hasToken) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "账号",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (state.loginSectionExpanded) {
                            "已登录，登录组件已展开"
                        } else if (state.phone.isBlank()) {
                            "已登录"
                        } else {
                            "已登录 " + maskedPhone(state.phone)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = vm::toggleLoginSection) {
                    Text(if (state.loginSectionExpanded) "收起" else "展开")
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (!state.hasToken || state.loginSectionExpanded) {
            OutlinedTextField(
                value = state.phone,
                onValueChange = vm::updatePhone,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("手机号") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(8.dp),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = vm::sendCode,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.sendingCode,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (state.sendingCode) "发送中" else "发送验证码")
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.code,
                onValueChange = vm::updateCode,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("验证码") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(8.dp),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = vm::login,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loggingIn,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (state.loggingIn) "登录中" else "确认登录")
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = vm::showCurrentToken,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("查看当前 Token")
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = vm::showOrderHistory,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("历史订单")
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { showLoginQr = true },
                modifier = Modifier.weight(1f),
                enabled = state.hasToken,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("登录二维码")
            }
            OutlinedButton(
                onClick = { showImportToken = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("导入登录")
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = vm::showAccountInfo,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.hasToken,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("账号信息")
        }

        Spacer(Modifier.height(28.dp))
        Divider()
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("资产", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            TextButton(onClick = vm::refreshBalance, enabled = state.hasToken && !state.loadingBalance) {
                Text("刷新")
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            !state.hasToken -> EmptyText("登录后展示资产信息")
            state.loadingBalance -> LoadingText("正在查询资产")
            state.balance != null -> {
                Text("通用小票：${state.balance.ticketText}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text("积分：${state.balance.pointsText}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text("积分抵扣金额：${state.balance.integralAmount ?: "-"}", style = MaterialTheme.typography.bodyLarge)
            }
            else -> EmptyText("暂无资产信息")
        }

        Spacer(Modifier.height(28.dp))
        Divider()
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "深色模式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (state.darkTheme) "已开启深色外观" else "已关闭，当前为浅色外观",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.darkTheme, onCheckedChange = vm::setDarkTheme)
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "运行日志",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        state.logging -> "正在记录接口调用，已记录 " + state.logLines + " 行"
                        state.logLines > 0 -> "已记录 " + state.logLines + " 行，可导出"
                        else -> "遇到报错时开启记录，方便排查"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = vm::toggleLogging,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(if (state.logging) "停止记录" else "开始记录")
            }
        }

        if (!state.logging && state.logLines > 0) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { exportLogLauncher.launch(vm.suggestedLogName()) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("导出日志")
                }
                OutlinedButton(
                    onClick = vm::clearLog,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("清空")
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        Spacer(Modifier.height(28.dp))
        Divider()
        Spacer(Modifier.height(18.dp))
        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_REPOSITORY_URL))
                runCatching { context.startActivity(intent) }
                    .onFailure {
                        Toast.makeText(context, "没有可用的浏览器打开链接", Toast.LENGTH_SHORT).show()
                    }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("关于")
        }
        Spacer(Modifier.height(18.dp))
    }

    if (showLoginQr) {
        LoginQrDialog(
            payload = vm.exportLoginQr().orEmpty(),
            onDismiss = { showLoginQr = false },
        )
    }

    if (showImportToken) {
        ImportTokenDialog(
            onDismiss = { showImportToken = false },
            onConfirm = { payload ->
                vm.importToken(payload)
                showImportToken = false
            },
        )
    }

    state.accountInfo?.let { info ->
        AccountInfoDialog(
            info = info,
            balance = state.balance,
            onDismiss = vm::dismissAccountInfo,
        )
    }
}

@Composable
private fun AccountInfoDialog(
    info: com.fakepangguai.qiekj.data.UserInfoData,
    balance: com.fakepangguai.qiekj.data.BalanceData?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("账号信息") },
        text = {
            Column {
                DetailLine("手机号", info.phone ?: "-")
                DetailLine("注册时间", info.registerTime ?: "-")
                DetailLine("现金余额", "¥" + (info.balance ?: balance?.balance ?: "-"))
                DetailLine("通用小票", balance?.ticketText ?: "-")
                DetailLine("积分", balance?.pointsText ?: "-")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        shape = RoundedCornerShape(8.dp),
    )
}
@Composable
private fun LoginQrDialog(
    payload: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap = remember(payload) {
        if (payload.isBlank()) {
            null
        } else {
            runCatching {
                BarcodeEncoder().encodeBitmap(payload, BarcodeFormat.QR_CODE, 720, 720)
            }.getOrNull()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("登录二维码") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "登录二维码",
                        modifier = Modifier.size(240.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "用另一台手机打开本 App，点「设备控制」右上角「扫码」扫这个码，即可免验证码登录同一账号",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("当前未登录，无法生成登录二维码")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("qiekj-login", payload))
                    Toast.makeText(context, "登录信息已复制", Toast.LENGTH_SHORT).show()
                },
            ) {
                Text("复制")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun ImportTokenDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入登录") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("粘贴登录内容或 Token") },
                    minLines = 2,
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "在已登录的设备上打开「我的 → 登录二维码」复制或扫码，粘贴到这里即可免验证码登录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun DeviceCodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设备号导入") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("设备号（SN / IMEI）") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "就是设备行里「SN」后面那串数字，也可以粘贴二维码里的链接",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("绑定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun OrderHistoryDialog(
    orders: List<OrderHistoryItem>,
    onDismiss: () -> Unit,
    onOpenOrder: (OrderHistoryItem) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("历史订单") },
        text = {
            if (orders.isEmpty()) {
                Text("暂无历史订单", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn {
                    items(orders) { item ->
                        OrderHistoryRow(item = item, onClick = { onOpenOrder(item) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        shape = RoundedCornerShape(8.dp),
    )
}
@Composable
private fun OrderHistoryRow(item: OrderHistoryItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(item.goodsName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(
            "订单：${item.orderNo}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "原价：${item.originPrice}  小票：${item.ticketCost}  积分：${item.integralCost}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Divider()
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceRow(
    name: String,
    subtitle: String?,
    canStart: Boolean,
    ending: Boolean,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onOpenMenu)
            .padding(vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onStart,
                enabled = canStart,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text("启动")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onEnd,
                enabled = canStart && !ending,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(if (ending) "关闭中" else "关闭")
            }
        }
        Spacer(Modifier.height(14.dp))
        Divider()
    }
}

@Composable
private fun DeviceMenuDialog(
    title: String,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onAddShortcut: () -> Unit,
    onCopyCode: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                MenuAction(text = "备注名字", onClick = onRename)
                MenuAction(text = "复制设备号", onClick = onCopyCode)
                MenuAction(text = "添加到桌面", onClick = onAddShortcut)
                if (canDelete) {
                    MenuAction(text = "删除记录", onClick = onDelete, danger = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun MenuAction(
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun RenameDeviceDialog(
    defaultName: String,
    currentAlias: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(currentAlias) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("备注名字") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("设备备注名") },
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "默认名称：" + defaultName + "，留空则恢复默认",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun LoadingText(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(strokeWidth = 2.dp)
            Spacer(Modifier.height(12.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
