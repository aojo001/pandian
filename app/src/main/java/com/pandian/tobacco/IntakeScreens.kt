package com.pandian.tobacco

import android.app.Activity
import android.widget.Toast
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.UUID

@Composable
fun IntakeRegistrationScreen(
    activity: Activity,
    store: LedgerStore,
    products: List<Product>,
    productCategories: List<String>,
    intakes: MutableList<IntakeRecord>,
    modifier: Modifier = Modifier
) {
    val cart = remember { mutableStateMapOf<String, IntakeLine>() }
    var editing by remember { mutableStateOf<Product?>(null) }
    var showCart by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("全部") }
    var barcode by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val scanFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun submitBarcode() {
        val code = normalizeBarcode(barcode)
        if (code.isBlank()) return
        val product = products.firstOrNull { normalizeBarcode(it.barcode).equals(code, true) }
        barcode = ""
        if (product == null) message = "没有找到条码 $code"
        else {
            message = "已识别：${product.name}"
            editing = product
        }
    }

    LaunchedEffect(barcode) {
        val code = normalizeBarcode(barcode)
        if (code.length >= 4) {
            delay(180)
            if (normalizeBarcode(barcode) == code) submitBarcode()
        }
    }

    LaunchedEffect(editing, showCart, showImport) {
        if (editing == null && !showCart && !showImport) {
            delay(100)
            runCatching { scanFocus.requestFocus() }
            keyboard?.hide()
        }
    }

    val selectableCategories = listOf("全部") + productCategories.filter { category ->
        products.any { it.category.ifBlank { "未分类" } == category }
    }
    val visible = products.filter {
        (it.name.contains(search.trim(), true) || it.barcode.contains(search.trim(), true)) &&
            (categoryFilter == "全部" || it.category.ifBlank { "未分类" } == categoryFilter)
    }
    val selected = cart.values.toList()

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("只登记品类和数量", fontSize = 17.sp, color = MaterialTheme.colorScheme.secondary)
            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it.filterNot(Char::isWhitespace).take(40) },
                label = { Text("扫描烟草条码（自动识别）") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submitBarcode() }),
                modifier = Modifier.fillMaxWidth().focusRequester(scanFocus).onPreviewKeyEvent { event ->
                    if (event.key == Key.Enter) {
                        if (event.type == KeyEventType.KeyDown) submitBarcode()
                        true
                    } else false
                }
            )
            if (message.isNotBlank()) Text(message, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it.take(30) },
                    label = { Text("搜索烟草") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { showImport = true }, modifier = Modifier.height(56.dp)) {
                    Text("一键导入", fontSize = 17.sp)
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(selectableCategories) { category ->
                    FilterChip(
                        selected = categoryFilter == category,
                        onClick = { categoryFilter = category },
                        label = { Text(category, fontSize = 16.sp) }
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(visible, key = { it.id }) { product ->
                IntakeProductCard(product, cart[product.id]?.quantity ?: 0) { editing = product }
            }
        }

        Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${selected.size} 款", fontSize = 17.sp, color = MaterialTheme.colorScheme.secondary)
                    Text("共 ${selected.sumOf { it.quantity }} 件", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    OutlinedButton(onClick = { showCart = true }, enabled = selected.isNotEmpty(), modifier = Modifier.height(44.dp)) {
                        Text("查看清单")
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            val record = store.addIntake(selected)
                            intakes.add(0, record)
                            cart.clear()
                            Toast.makeText(activity, "入库登记已保存，等待结算", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.height(56.dp)
                    ) { Text("保存入库", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    editing?.let { product ->
        QuantityOnlyDialog(
            product = product,
            initial = cart[product.id]?.quantity ?: 0,
            onDismiss = { editing = null },
            onSave = { quantity ->
                if (quantity > 0) cart[product.id] = IntakeLine(product, quantity) else cart.remove(product.id)
                editing = null
            }
        )
    }

    if (showCart) {
        IntakeCartDialog(
            lines = selected,
            onDismiss = { showCart = false },
            onEdit = { product -> showCart = false; editing = product },
            onRemove = { productId -> cart.remove(productId) }
        )
    }

    if (showImport) {
        BulkImportDialog(
            onDismiss = { showImport = false },
            onImport = { text ->
                val (parsed, invalid) = parseBulkIntake(text)
                parsed.forEach { item ->
                    val official = products.firstOrNull { it.name.trim().equals(item.name, true) }
                    val existing = cart.values.firstOrNull { it.product.name.trim().equals(item.name, true) }
                    val product = official ?: existing?.product ?: Product(
                        id = "manual-${UUID.randomUUID()}", name = item.name,
                        referencePrice = 0.0, receivePrice = 0.0, retailPrice = 0.0, wholesalePrice = 0.0,
                        unit = item.unit
                    )
                    val previous = cart[product.id]?.quantity ?: existing?.quantity ?: 0
                    existing?.let { if (it.product.id != product.id) cart.remove(it.product.id) }
                    cart[product.id] = IntakeLine(product, previous + item.quantity)
                }
                showImport = false
                message = when {
                    parsed.isEmpty() -> "没有识别到可导入的内容"
                    invalid.isEmpty() -> "已导入 ${parsed.size} 款烟草"
                    else -> "已导入 ${parsed.size} 款，${invalid.size} 行未识别"
                }
            }
        )
    }
}

@Composable
private fun IntakeProductCard(product: Product, quantity: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (quantity > 0) MaterialTheme.colorScheme.primaryContainer else Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(product.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("单位：${product.unit}", fontSize = 15.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(10.dp))
            Text(if (quantity > 0) "$quantity ${product.unit}" else "点击填数量", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun QuantityOnlyDialog(product: Product, initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var quantity by remember(product.id) { mutableStateOf(initial.coerceAtLeast(1).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name, fontSize = 24.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("本次入库数量", color = MaterialTheme.colorScheme.secondary, fontSize = 17.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { quantity = ((quantity.toIntOrNull() ?: 1) - 1).coerceAtLeast(0).toString() }, modifier = Modifier.size(56.dp)) { Text("−", fontSize = 24.sp) }
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.filter(Char::isDigit).take(6) },
                        suffix = { Text(product.unit) },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { quantity = ((quantity.toIntOrNull() ?: 0) + 1).toString() }, modifier = Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) { Text("+", fontSize = 24.sp) }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(quantity.toIntOrNull()?.coerceAtLeast(0) ?: 0) }) { Text("确定", fontSize = 18.sp) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun IntakeCartDialog(
    lines: List<IntakeLine>,
    onDismiss: () -> Unit,
    onEdit: (Product) -> Unit,
    onRemove: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本次入库清单", fontSize = 23.sp, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(lines, key = { it.product.id }) { line ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(line.product.name, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Text("${line.quantity}${line.product.unit}", fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = { onEdit(line.product) }) { Text("修改") }
                            TextButton(onClick = { onRemove(line.product.id) }) { Text("删除", color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("完成") } }
    )
}

private data class BulkIntakeItem(val name: String, val quantity: Int, val unit: String)

private fun parseBulkIntake(text: String): Pair<List<BulkIntakeItem>, List<String>> {
    val parsed = mutableListOf<BulkIntakeItem>()
    val invalid = mutableListOf<String>()
    val pattern = Regex("""^(.+?)[\s:：xX×*]*(\d+)\s*(条|盒|包|件)?$""")
    text.split(Regex("[\\n,，;；]+")).map(String::trim).filter(String::isNotBlank).forEach { line ->
        val match = pattern.matchEntire(line)
        val quantity = match?.groupValues?.getOrNull(2)?.toIntOrNull()
        val name = match?.groupValues?.getOrNull(1)?.trim()?.trimEnd(':', '：', 'x', 'X', '×', '*')?.trim().orEmpty()
        if (match == null || quantity == null || quantity <= 0 || name.isBlank()) {
            invalid += line
        } else {
            parsed += BulkIntakeItem(name, quantity, match.groupValues.getOrNull(3).orEmpty().ifBlank { "条" })
        }
    }
    return parsed to invalid
}

@Composable
private fun BulkImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("一键导入入库清单", fontSize = 23.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("每行一款，名称不需要与商品库一致", color = MaterialTheme.colorScheme.secondary)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(3000) },
                    placeholder = { Text("红南京35条\n绿钗15条\n纯净7条\n猴王7条\n阳光利群24条\n小利群7条") },
                    minLines = 8,
                    maxLines = 14,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(onClick = { onImport(text) }, enabled = text.isNotBlank()) { Text("导入清单") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun SettlementScreen(
    intakes: List<IntakeRecord>,
    modifier: Modifier = Modifier,
    onSettle: (IntakeRecord, List<CartLine>) -> Unit
) {
    var selectedId by remember { mutableStateOf<String?>(intakes.firstOrNull()?.id) }
    val selected = intakes.firstOrNull { it.id == selectedId } ?: intakes.firstOrNull()
    val prices = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(selected?.id) {
        prices.clear()
        selected?.lines?.forEach { prices[it.product.id] = "" }
    }

    if (selected == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("没有待结算入库单", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("请先完成登记入库", fontSize = 17.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }
        return
    }

    val cart = selected.lines.mapNotNull { line ->
        prices[line.product.id]?.toDoubleOrNull()?.takeIf { it > 0 }?.let { CartLine(line.product, it, line.quantity) }
    }
    val ready = cart.size == selected.lines.size

    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("选择待结算入库单", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(intakes, key = { it.id }) { record ->
                        FilterChip(
                            selected = record.id == selected.id,
                            onClick = { selectedId = record.id },
                            label = { Text("${record.displayTime.substring(5)} · ${record.totalQuantity}件") }
                        )
                    }
                }
            }
            item {
                Text("逐款填写收烟单价", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("入库时间：${selected.displayTime}", fontSize = 15.sp, color = MaterialTheme.colorScheme.secondary)
            }
            items(selected.lines, key = { it.product.id }) { line ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(line.product.name, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text("${line.quantity}${line.product.unit}", fontSize = 18.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                        OutlinedTextField(
                            value = prices[line.product.id].orEmpty(),
                            onValueChange = { input -> prices[line.product.id] = input.filter { it.isDigit() || it == '.' }.take(9) },
                            label = { Text("单价") },
                            prefix = { Text("￥") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.width(150.dp)
                        )
                    }
                }
            }
        }
        Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (ready) "结算合计" else "请填写全部单价", fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
                    Text("￥${money(cart.sumOf { it.subtotal })}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Button(enabled = ready, onClick = { onSettle(selected, cart) }, modifier = Modifier.height(60.dp)) {
                    Text("确认结算", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
