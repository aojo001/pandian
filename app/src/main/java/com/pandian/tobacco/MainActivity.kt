package com.pandian.tobacco

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TobaccoTheme { TobaccoLedgerApp(this) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TobaccoLedgerApp(activity: Activity) {
    val store = remember { LedgerStore(activity) }
    val products = remember { mutableStateListOf<Product>().apply { addAll(store.loadProducts()) } }
    val orders = remember { mutableStateListOf<TradeOrder>().apply { addAll(store.loadOrders()) } }
    val intakes = remember { mutableStateListOf<IntakeRecord>().apply { addAll(store.loadIntakes()) } }
    val customerStore = remember { CustomerStore(activity) }
    val customers = remember {
        mutableStateListOf<Customer>().apply {
            addAll(customerStore.migrateLegacyPeople(AccountingStore(activity).loadPeople()))
        }
    }
    var currentPage by remember { mutableStateOf(AppPage.HOME) }
    var managementMode by remember { mutableStateOf(false) }
    var settlementPrintOrder by remember { mutableStateOf<TradeOrder?>(null) }
    var availableUpdate by remember { mutableStateOf<GitHubReleaseUpdate?>(null) }

    LaunchedEffect(Unit) {
        GitHubUpdateManager.checkForUpdate { result ->
            availableUpdate = result.getOrNull()
        }
    }

    BackHandler(enabled = currentPage != AppPage.HOME) {
        currentPage = AppPage.HOME
    }

    Scaffold(
        topBar = {
            if (currentPage != AppPage.HOME) {
                TopAppBar(
                    navigationIcon = { TextButton(onClick = { currentPage = AppPage.HOME }) { Text("‹", fontSize = 34.sp) } },
                    title = { Text(currentPage.title, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                        navigationIconContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    ) { padding ->
        when (currentPage) {
            AppPage.HOME -> HomeScreen(
                pendingCount = intakes.size,
                managementMode = managementMode,
                onUnlockManagement = { managementMode = true },
                onExitManagement = { managementMode = false },
                onNavigate = { currentPage = it },
                modifier = Modifier.padding(padding)
            )
            AppPage.INTAKE -> IntakeRegistrationScreen(activity, store, products, intakes, Modifier.padding(padding))
            AppPage.SETTLEMENT -> SettlementScreen(
                intakes = intakes,
                modifier = Modifier.padding(padding),
                onSettle = { intake, lines ->
                    val order = store.newOrder(TradeType.RECEIVE, "", lines)
                    store.addOrder(order)
                    orders.add(0, order)
                    store.deleteIntake(intake.id)
                    intakes.removeAll { it.id == intake.id }
                    settlementPrintOrder = order
                }
            )
            AppPage.TRADE -> TradeScreen(activity, store, products, orders, customers, Modifier.padding(padding))
            AppPage.PRICES -> ProductManagementScreen(activity, store, products, Modifier.padding(padding))
            AppPage.HISTORY -> HistoryScreen(activity, orders, Modifier.padding(padding))
            AppPage.ACCOUNTING -> BookkeepingScreen(activity, customerStore, customers, Modifier.padding(padding))
            AppPage.CUSTOMERS -> CustomerManagementScreen(activity, customerStore, customers, Modifier.padding(padding))
            AppPage.STATS -> StatisticsScreen(activity, orders, Modifier.padding(padding))
            AppPage.SETTINGS -> SettingsScreen(activity, Modifier.padding(padding))
        }
    }

    settlementPrintOrder?.let { order ->
        PrintDialog(activity, order, onDismiss = { settlementPrintOrder = null })
    }
    availableUpdate?.let { update ->
        GitHubUpdateDialog(activity, update, onDismiss = { availableUpdate = null })
    }
}

enum class AppPage(val title: String) {
    HOME("烟收宝"), INTAKE("登记入库"), SETTLEMENT("结算"), TRADE("收烟 / 卖烟登记"), ACCOUNTING("记一笔账"), PRICES("烟价管理"), HISTORY("交易记录"),
    CUSTOMERS("客户管理"), STATS("数据统计"), SETTINGS("设置")
}

@Composable
private fun TradeScreen(
    activity: Activity,
    store: LedgerStore,
    products: MutableList<Product>,
    orders: MutableList<TradeOrder>,
    customers: List<Customer>,
    modifier: Modifier = Modifier
) {
    var tradeType by remember { mutableStateOf(TradeType.RECEIVE) }
    val quantities = remember { mutableStateMapOf<String, Int>() }
    val customPrices = remember { mutableStateMapOf<String, Double>() }
    var editing by remember { mutableStateOf<Product?>(null) }
    var showCheckout by remember { mutableStateOf(false) }
    var printOrder by remember { mutableStateOf<TradeOrder?>(null) }
    var searchText by remember { mutableStateOf("") }
    var quickFilter by remember { mutableStateOf("全部") }
    var scannedBarcode by remember { mutableStateOf("") }
    var scanMessage by remember { mutableStateOf("") }
    var scanSucceeded by remember { mutableStateOf(false) }
    val scanFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun submitBarcode() {
        val code = normalizeBarcode(scannedBarcode)
        if (code.isBlank()) return
        val product = products.firstOrNull { normalizeBarcode(it.barcode).equals(code, ignoreCase = true) }
        scannedBarcode = ""
        if (product == null) {
            scanSucceeded = false
            scanMessage = "未找到条码 $code，请先到烟价管理录入"
        } else {
            scanSucceeded = true
            scanMessage = "已识别：${product.name}"
            editing = product
        }
    }

    LaunchedEffect(editing, showCheckout, printOrder) {
        if (editing == null && !showCheckout && printOrder == null) {
            delay(120)
            runCatching { scanFocusRequester.requestFocus() }
            keyboardController?.hide()
        }
    }

    fun unitPrice(product: Product): Double = customPrices[product.id] ?: when (tradeType) {
        TradeType.RECEIVE -> product.receivePrice
        TradeType.BUY -> product.retailPrice
    }

    val cart = products.mapNotNull { product ->
        val quantity = quantities[product.id] ?: 0
        if (quantity > 0) CartLine(product, unitPrice(product), quantity) else null
    }
    val filterOptions = listOf("全部", "已选") + products.map { it.name }
    val filteredProducts = products.filter { product ->
        val matchesSearch = product.name.contains(searchText.trim(), ignoreCase = true)
        val matchesQuickFilter = when (quickFilter) {
            "全部" -> true
            "已选" -> (quantities[product.id] ?: 0) > 0
            else -> product.name == quickFilter
        }
        matchesSearch && matchesQuickFilter
    }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            ChoiceRow(
                options = TradeType.entries,
                selected = tradeType,
                label = { it.title },
                onSelect = { tradeType = it; customPrices.clear() }
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = scannedBarcode,
                    onValueChange = { scannedBarcode = it.filterNot(Char::isWhitespace).take(40) },
                    label = { Text("扫码枪条码") },
                    placeholder = { Text("扫描后自动识别") },
                    supportingText = { Text("扫码枪使用键盘模式，并设置回车后缀") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitBarcode() }),
                    modifier = Modifier.weight(1f).focusRequester(scanFocusRequester).onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter) {
                            if (event.type == KeyEventType.KeyDown) submitBarcode()
                            true
                        } else false
                    }
                )
                Button(onClick = { submitBarcode() }, modifier = Modifier.height(64.dp)) {
                    Text("识别", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (scanMessage.isNotBlank()) {
                Text(
                    scanMessage,
                    color = if (scanSucceeded) Color(0xFF167A3E) else MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it.take(30) },
                label = { Text("搜索烟草名称") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
                modifier = Modifier.fillMaxWidth()
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterOptions) { filter ->
                    FilterChip(
                        selected = quickFilter == filter,
                        onClick = { quickFilter = filter },
                        label = { Text(filter, fontSize = 18.sp) }
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(180.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredProducts, key = { it.id }) { product ->
                ProductCard(
                    product = product,
                    priceLabel = tradeType.priceLabel,
                    unitPrice = unitPrice(product),
                    quantity = quantities[product.id] ?: 0,
                    onEdit = { editing = product },
                    onQuantity = { quantities[product.id] = it.coerceAtLeast(0) }
                )
            }
        }

        Surface(shadowElevation = 10.dp, color = Color.White) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("已选 ${cart.size} 款", fontSize = 18.sp, color = MaterialTheme.colorScheme.secondary)
                    Text("￥${money(cart.sumOf { it.subtotal })}", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Button(
                    onClick = { showCheckout = true },
                    enabled = cart.isNotEmpty(),
                    modifier = Modifier.height(64.dp)
                ) { Text("去结算", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }

    editing?.let { product ->
        ProductEditDialog(
            product = product,
            initialPrice = unitPrice(product),
            initialQuantity = quantities[product.id] ?: 0,
            priceLabel = tradeType.priceLabel,
            onDismiss = { editing = null },
            onSave = { currentPrice, quantity ->
                customPrices[product.id] = currentPrice
                quantities[product.id] = quantity
                editing = null
            }
        )
    }

    if (showCheckout) {
        CheckoutDialog(
            tradeType = tradeType,
            cart = cart,
            customers = customers,
            onDismiss = { showCheckout = false },
            onConfirm = { customerName ->
                val order = store.newOrder(tradeType, customerName, cart)
                store.addOrder(order)
                orders.add(0, order)
                quantities.clear()
                customPrices.clear()
                showCheckout = false
                printOrder = order
            }
        )
    }

    printOrder?.let { order ->
        val settings = remember(order) { AppSettingsStore(activity).load() }
        if (settings.defaultPrinterAddress.isNotBlank()) {
            AutoPrint(
                activity, order,
                onDone = { printOrder = null },
                onNoPrinter = { printOrder = null }
            )
        } else {
            PrintDialog(activity, order, onDismiss = { printOrder = null })
        }
    }
}

@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val active = option == selected
            if (active) {
                Button(onClick = { onSelect(option) }, modifier = Modifier.weight(1f).height(60.dp)) { Text(label(option), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            } else {
                OutlinedButton(onClick = { onSelect(option) }, modifier = Modifier.weight(1f).height(60.dp)) { Text(label(option), fontSize = 22.sp) }
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: Product,
    priceLabel: String,
    unitPrice: Double,
    quantity: Int,
    onEdit: () -> Unit,
    onQuantity: (Int) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.fillMaxWidth().height(126.dp).clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                ProductImage(product)
                Text("本单编辑", Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)).padding(5.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            Text(product.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("单位：${product.unit}", color = MaterialTheme.colorScheme.secondary, fontSize = 16.sp)
            Text("参考价 ￥${money(product.referencePrice)}", color = MaterialTheme.colorScheme.secondary, fontSize = 17.sp)
            Text("$priceLabel ￥${money(unitPrice)}", color = MaterialTheme.colorScheme.primary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallQuantityButton("−", quantity > 0) { onQuantity(quantity - 1) }
                Text("$quantity ${product.unit}", Modifier.width(76.dp), textAlign = TextAlign.Center, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                SmallQuantityButton("+", true) { onQuantity(quantity + 1) }
            }
        }
    }
}

@Composable
private fun ProductImage(product: Product) {
    val bitmap = remember(product.imagePath) {
        product.imagePath?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    }
    when {
        bitmap != null -> Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "${product.name}图片",
            modifier = Modifier.size(92.dp, 120.dp),
            contentScale = ContentScale.Fit
        )
        product.id == "red-nanjing" -> Image(
            painterResource(R.drawable.red_nanjing_pack), "红南京烟盒", Modifier.height(120.dp)
        )
        else -> Box(
            Modifier.size(82.dp, 116.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) { Text(product.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun SmallQuantityButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        contentPadding = PaddingValues(0.dp), modifier = Modifier.size(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary)
    ) { Text(text, fontSize = 24.sp) }
}

@Composable
private fun ProductEditDialog(
    product: Product,
    initialPrice: Double,
    initialQuantity: Int,
    priceLabel: String,
    onDismiss: () -> Unit,
    onSave: (Double, Int) -> Unit
) {
    var current by remember { mutableStateOf(money(initialPrice)) }
    var quantity by remember { mutableStateOf(initialQuantity.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${product.name} · 本单编辑", fontSize = 22.sp) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("默认$priceLabel：￥${money(initialPrice)} / ${product.unit}", fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary) }
                item { PriceField("本单自定义价", current) { current = it } }
                item {
                    OutlinedTextField(
                        value = quantity, onValueChange = { quantity = it.filter(Char::isDigit) },
                        label = { Text("本单数量（${product.unit}）") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(current.toDoubleOrNull() ?: initialPrice, quantity.toIntOrNull() ?: initialQuantity)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun PriceField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValue(input.filter { it.isDigit() || it == '.' }.take(9)) },
        label = { Text(label) }, prefix = { Text("￥") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CheckoutDialog(
    tradeType: TradeType,
    cart: List<CartLine>,
    customers: List<Customer>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认${tradeType.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${cart.size} 款烟草", fontSize = 17.sp)
                if (customers.isNotEmpty()) {
                    Text("选择固定客户", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(customers, key = { it.id }) { customer ->
                            FilterChip(
                                selected = name == customer.name,
                                onClick = { name = customer.name },
                                label = { Text(customer.name, fontSize = 17.sp) }
                            )
                        }
                    }
                }
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("客户姓名（可临时输入）") }, textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp), singleLine = true)
                HorizontalDivider()
                cart.forEach { Text("${it.product.name}  × ${it.quantity}${it.product.unit}    ￥${money(it.subtotal)}") }
                Text("合计 ￥${money(cart.sumOf { it.subtotal })}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(name.trim()) }) { Text("确认结算并打印") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("返回") } }
    )
}

@Composable
private fun PrintDialog(activity: Activity, order: TradeOrder, onDismiss: () -> Unit) {
    val settingsStore = remember { AppSettingsStore(activity) }
    var savedPrinter by remember { mutableStateOf(settingsStore.load().defaultPrinterAddress) }
    var devices by remember { mutableStateOf(emptyList<PrinterDevice>()) }
    var message by remember { mutableStateOf("") }
    val requestBluetooth = ReceiptPrinter.rememberBluetoothPermissionRequest()

    fun loadDevices() {
        requestBluetooth { granted ->
            if (!granted) {
                message = "未授权蓝牙权限，可使用系统打印"
                return@requestBluetooth
            }
            devices = ReceiptPrinter.pairedDevices()
            if (devices.isEmpty()) message = "未找到已配对设备，请先在系统蓝牙中配对打印机"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("打印交易小票") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("订单已保存 · ￥${money(order.total)}", fontWeight = FontWeight.Bold)
                Button(onClick = { ReceiptPrinter.printWithSystem(activity, order); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("使用系统打印")
                }
                OutlinedButton(onClick = { loadDevices() }, modifier = Modifier.fillMaxWidth()) { Text("查找蓝牙小票机") }
                if (message.isNotBlank()) Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                devices.forEach { device ->
                    OutlinedButton(
                        onClick = {
                            message = "正在连接 ${device.name}…"
                            settingsStore.savePrinter(device.name, device.address)
                            savedPrinter = device.address
                            ReceiptPrinter.printBluetooth(activity, device.address, order) { result ->
                                result.onSuccess {
                                    Toast.makeText(activity, "小票打印成功", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }.onFailure { message = "打印失败：${it.message ?: "请检查打印机"}" }
                            }
                        }, modifier = Modifier.fillMaxWidth()
                    ) { Text("${if (savedPrinter == device.address) "默认 · " else ""}${device.name}\n${device.address}", textAlign = TextAlign.Center) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后打印") } }
    )
}

@Composable
private fun HistoryScreen(activity: Activity, orders: List<TradeOrder>, modifier: Modifier = Modifier) {
    val requestBluetooth = ReceiptPrinter.rememberBluetoothPermissionRequest()
    if (orders.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无交易记录\n完成第一笔交易后，小票会保存在这里", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.secondary)
        }
        return
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("历史交易", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        items(orders, key = { it.id }) { order ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(order.tradeType.title, fontWeight = FontWeight.Bold)
                        Text("￥${money(order.total)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(order.displayTime, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                    if (order.customerName.isNotBlank()) Text("客户：${order.customerName}")
                    Text(order.lines.joinToString("、") { "${it.product.name}×${it.quantity}${it.product.unit}" }, fontSize = 14.sp)
                    TextButton(
                        onClick = {
                            val settings = AppSettingsStore(activity).load()
                            if (settings.defaultPrinterAddress.isBlank()) {
                                ReceiptPrinter.printWithSystem(activity, order)
                            } else {
                                requestBluetooth { granted ->
                                    if (!granted) {
                                        Toast.makeText(activity, "未授权蓝牙权限，改用系统打印", Toast.LENGTH_SHORT).show()
                                        ReceiptPrinter.printWithSystem(activity, order)
                                        return@requestBluetooth
                                    }
                                    ReceiptPrinter.printBluetooth(activity, settings.defaultPrinterAddress, order) { result ->
                                        result.onSuccess { Toast.makeText(activity, "小票打印成功", Toast.LENGTH_SHORT).show() }
                                            .onFailure { Toast.makeText(activity, "打印失败：${it.message ?: "请检查打印机"}", Toast.LENGTH_LONG).show() }
                                    }
                                }
                            }
                        },
                        Modifier.align(Alignment.End)
                    ) { Text("重新打印") }
                }
            }
        }
    }
}
