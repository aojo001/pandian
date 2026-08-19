package com.pandian.tobacco

import android.app.Activity
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

@Composable
fun ProductManagementScreen(
    activity: Activity,
    store: LedgerStore,
    products: MutableList<Product>,
    modifier: Modifier = Modifier
) {
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Product?>(null) }
    val tablet = LocalConfiguration.current.screenWidthDp >= 700
    val visible = products.filter {
        it.name.contains(search.trim(), ignoreCase = true) || it.barcode.contains(search.trim(), ignoreCase = true)
    }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = if (tablet) 24.dp else 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("商品资料管理", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("条码、图片、单位和价格", color = MaterialTheme.colorScheme.secondary, fontSize = 16.sp)
                }
                Button(onClick = {
                    editing = Product(
                        id = UUID.randomUUID().toString(), name = "", referencePrice = 0.0,
                        receivePrice = 0.0, retailPrice = 0.0, wholesalePrice = 0.0
                    )
                }, modifier = Modifier.height(48.dp)) { Text("＋ 新增烟草", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it.take(30) },
                label = { Text("搜索烟草名称或条码") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有匹配的商品，可点击右上角新增", color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (tablet) 250.dp else 174.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(if (tablet) 24.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visible, key = { it.id }) { product ->
                    ManagedProductCard(product) { editing = product }
                }
            }
        }
    }

    editing?.let { product ->
        ProductMasterDialog(
            activity = activity,
            store = store,
            initial = product,
            isNew = products.none { it.id == product.id },
            onDismiss = { editing = null },
            onSave = { saved ->
                val duplicate = products.any {
                    it.id != saved.id && it.barcode.trim().equals(saved.barcode.trim(), ignoreCase = true)
                }
                if (duplicate) {
                    Toast.makeText(activity, "这个条码已经属于其他烟草，请检查", Toast.LENGTH_LONG).show()
                    return@ProductMasterDialog
                }
                val index = products.indexOfFirst { it.id == saved.id }
                if (index >= 0) products[index] = saved else products.add(0, saved)
                store.saveProducts(products)
                editing = null
                Toast.makeText(activity, "商品资料已保存", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun ManagedProductCard(product: Product, onEdit: () -> Unit) {
    Card(
        onClick = onEdit,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(128.dp), contentAlignment = Alignment.Center) {
                ManagedProductImage(product, 112)
                Text(
                    "编辑资料",
                    modifier = Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 5.dp),
                    color = MaterialTheme.colorScheme.primary, fontSize = 12.sp
                )
            }
            Text(product.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("单位：${product.unit}", fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
            Text(
                if (product.barcode.isBlank()) "条码：未录入" else "条码：${product.barcode}",
                fontSize = 14.sp,
                color = if (product.barcode.isBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            PriceLine("参考价", product.referencePrice)
            PriceLine("默认收烟价", product.receivePrice)
            PriceLine("默认卖烟价", product.retailPrice)
        }
    }
}

@Composable
private fun PriceLine(label: String, price: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.secondary, fontSize = 15.sp)
        Text("￥${money(price)}", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
private fun ProductMasterDialog(
    activity: Activity,
    store: LedgerStore,
    initial: Product,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var barcode by remember(initial.id) { mutableStateOf(initial.barcode) }
    var unit by remember(initial.id) { mutableStateOf(initial.unit) }
    var reference by remember(initial.id) { mutableStateOf(money(initial.referencePrice)) }
    var receive by remember(initial.id) { mutableStateOf(money(initial.receivePrice)) }
    var buy by remember(initial.id) { mutableStateOf(money(initial.retailPrice)) }
    var imagePath by remember(initial.id) { mutableStateOf(initial.imagePath) }
    var showPhotoSource by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "新增烟草" else "编辑商品资料", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ManagedProductImage(initial.copy(name = name.ifBlank { "新商品" }, imagePath = imagePath), 82)
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(onClick = { showPhotoSource = true }, modifier = Modifier.weight(1f)) {
                            Text(if (imagePath == null) "上传烟草图片" else "更换烟草图片")
                        }
                    }
                }
                item { OutlinedTextField(name, { name = it.take(30) }, label = { Text("烟草名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { input -> barcode = input.filterNot(Char::isWhitespace).take(40) },
                        label = { Text("条码号（每款烟唯一）") },
                        supportingText = { Text("可直接用扫码枪扫入") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("计量单位", fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(listOf("条", "盒", "包")) { item ->
                            FilterChip(selected = unit == item, onClick = { unit = item }, label = { Text(item) })
                        }
                    }
                    OutlinedTextField(unit, { unit = it.take(6) }, label = { Text("单位（可自定义）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                item { MasterPriceField("市场参考价", reference) { reference = it } }
                item { MasterPriceField("默认收烟价", receive) { receive = it } }
                item { MasterPriceField("默认卖烟价", buy) { buy = it } }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && barcode.isNotBlank() && unit.isNotBlank(),
                onClick = {
                    val buyPrice = buy.toDoubleOrNull() ?: initial.retailPrice
                    onSave(
                        initial.copy(
                            name = name.trim(), barcode = normalizeBarcode(barcode), unit = unit.trim(), imagePath = imagePath,
                            referencePrice = reference.toDoubleOrNull() ?: initial.referencePrice,
                            receivePrice = receive.toDoubleOrNull() ?: initial.receivePrice,
                            retailPrice = buyPrice, wholesalePrice = buyPrice
                        )
                    )
                }
            ) { Text("保存资料") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showPhotoSource) {
        PhotoSourceDialog(
            activity = activity,
            onDismiss = { showPhotoSource = false },
            onSelected = { uri ->
                val saved = store.saveProductImage(initial.id, uri)
                if (saved == null) Toast.makeText(activity, "图片保存失败，请重新选择", Toast.LENGTH_SHORT).show()
                else imagePath = saved
            }
        )
    }
}

@Composable
private fun MasterPriceField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValue(input.filter { it.isDigit() || it == '.' }.take(9)) },
        label = { Text(label) }, prefix = { Text("￥") }, suffix = { Text("元") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ManagedProductImage(product: Product, height: Int) {
    val bitmap = remember(product.imagePath) {
        product.imagePath?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    }
    when {
        bitmap != null -> Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "${product.name}图片",
            modifier = Modifier.height(height.dp).width((height * .76).dp),
            contentScale = ContentScale.Fit
        )
        product.id == "red-nanjing" -> Image(
            painterResource(R.drawable.red_nanjing_pack), "红南京烟盒", Modifier.height(height.dp), contentScale = ContentScale.Fit
        )
        else -> Box(
            Modifier.width((height * .72).dp).height(height.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) { Text(product.name.ifBlank { "烟草" }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 2) }
    }
}
