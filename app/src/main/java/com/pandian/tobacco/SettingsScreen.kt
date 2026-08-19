package com.pandian.tobacco

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(activity: Activity, modifier: Modifier = Modifier) {
    val store = remember { AppSettingsStore(activity) }
    val initial = remember { store.load() }
    var shopName by remember { mutableStateOf(initial.shopName) }
    var phone by remember { mutableStateOf(initial.phone) }
    var address by remember { mutableStateOf(initial.address) }
    var receiptTitle by remember { mutableStateOf(initial.receiptTitle) }
    var receiptFooter by remember { mutableStateOf(initial.receiptFooter) }
    var paperWidth by remember { mutableStateOf(initial.paperWidth) }
    var printerName by remember { mutableStateOf(initial.defaultPrinterName) }
    var printerAddress by remember { mutableStateOf(initial.defaultPrinterAddress) }
    var managerPin by remember { mutableStateOf(initial.managerPin) }
    var showRestoreAuth by remember { mutableStateOf(false) }
    var restorePin by remember { mutableStateOf("") }
    var showPrinterPicker by remember { mutableStateOf(false) }
    var pairedDevices by remember { mutableStateOf(emptyList<PrinterDevice>()) }
    val requestBluetooth = ReceiptPrinter.rememberBluetoothPermissionRequest()
    val validManagerPin = managerPin.isBlank() || managerPin.length >= 4

    fun currentSettings() = AppSettings(
        shopName.trim().ifBlank { "烟收宝" }, phone.trim(), address.trim(),
        receiptTitle.trim().ifBlank { "烟草交易小票" },
        receiptFooter.trim().ifBlank { "谢谢惠顾，请核对小票" },
        paperWidth, printerName, printerAddress, managerPin
    )

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            store.save(currentSettings())
            AppDataBackup.export(activity, uri)
                .onSuccess { Toast.makeText(activity, "数据备份成功", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(activity, "备份失败：${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            AppDataBackup.restore(activity, uri)
                .onSuccess {
                    val restored = store.load()
                    shopName = restored.shopName; phone = restored.phone; address = restored.address
                    receiptTitle = restored.receiptTitle; receiptFooter = restored.receiptFooter
                    paperWidth = restored.paperWidth; printerName = restored.defaultPrinterName; printerAddress = restored.defaultPrinterAddress
                    managerPin = restored.managerPin
                    Toast.makeText(activity, "数据恢复成功，重新进入页面后生效", Toast.LENGTH_LONG).show()
                }
                .onFailure { Toast.makeText(activity, "恢复失败：${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth().widthIn(max = 900.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsSection(Icons.Rounded.Storefront, "店铺信息", "用于经营信息和小票抬头") {
                OutlinedTextField(shopName, { shopName = it.take(30) }, label = { Text("店铺名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it.filter { char -> char.isDigit() || char == '-' }.take(20) }, label = { Text("联系电话") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(address, { address = it.take(80) }, label = { Text("店铺地址") }, modifier = Modifier.fillMaxWidth())
            }
        }
        item {
            SettingsSection(Icons.Rounded.Print, "打印设置", "选择纸宽并设置实际打印内容") {
                OutlinedTextField(receiptTitle, { receiptTitle = it.take(30) }, label = { Text("小票标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(receiptFooter, { receiptFooter = it.take(60) }, label = { Text("小票页脚") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("小票纸宽", color = MaterialTheme.colorScheme.secondary)
                    FilterChip(selected = paperWidth == 58, onClick = { paperWidth = 58 }, label = { Text("58mm") })
                    FilterChip(selected = paperWidth == 80, onClick = { paperWidth = 80 }, label = { Text("80mm") })
                }
                Text(
                    if (printerAddress.isBlank()) "默认蓝牙打印机：尚未选择"
                    else "默认蓝牙打印机：$printerName · $printerAddress",
                    color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp
                )
                OutlinedButton(
                    onClick = {
                        requestBluetooth { granted ->
                            if (!granted) {
                                Toast.makeText(activity, "未授权蓝牙权限，无法读取蓝牙设备", Toast.LENGTH_LONG).show()
                                return@requestBluetooth
                            }
                            pairedDevices = ReceiptPrinter.pairedDevices()
                            showPrinterPicker = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (printerAddress.isBlank()) "选择默认蓝牙打印机" else "更换默认打印机") }
                if (printerAddress.isNotBlank()) {
                    OutlinedButton(onClick = { printerName = ""; printerAddress = "" }, modifier = Modifier.fillMaxWidth()) { Text("清除默认打印机") }
                }
            }
        }
        item {
            SettingsSection(Icons.Rounded.Backup, "数据备份", "备份包含业务数据、设置和上传图片") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (validManagerPin) {
                                val name = "烟收宝备份-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date())}.json"
                                exportLauncher.launch(name)
                            } else Toast.makeText(activity, "请先设置至少4位的管理密码", Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.weight(1f)
                    ) { Text("导出备份") }
                    OutlinedButton(
                        onClick = {
                            if (!validManagerPin) Toast.makeText(activity, "请先设置至少4位的管理密码", Toast.LENGTH_SHORT).show()
                            else if (managerPin.isBlank()) restoreLauncher.launch(arrayOf("application/json", "text/plain"))
                            else { restorePin = ""; showRestoreAuth = true }
                        }, modifier = Modifier.weight(1f)
                    ) { Text("恢复数据") }
                }
            }
        }
        item {
            SettingsSection(Icons.Rounded.Lock, "安全设置", "管理密码用于保护数据恢复操作") {
                OutlinedTextField(
                    managerPin,
                    { managerPin = it.filter(Char::isDigit).take(12) },
                    label = { Text("管理密码（4-12位数字，可留空）") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (managerPin.isNotBlank() && managerPin.length < 4) {
                    Text("管理密码至少需要 4 位", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        }
        item {
            Button(
                onClick = {
                    store.save(currentSettings())
                    Toast.makeText(activity, "设置已保存", Toast.LENGTH_SHORT).show()
                }, enabled = validManagerPin, modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("保存全部设置") }
        }
        item {
            SettingsSection(Icons.Rounded.Info, "关于", "烟收宝 Android 版") {
                Text("版本 1.0.0 · 本地离线存储", color = MaterialTheme.colorScheme.secondary)
                Text("备份文件包含业务数据、设置、烟草图片和人员头像。", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
            }
        }
    }

    if (showRestoreAuth) {
        AlertDialog(
            onDismissRequest = { showRestoreAuth = false },
            title = { Text("验证管理密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("恢复数据会覆盖当前业务记录，请输入管理密码。")
                    OutlinedTextField(
                        restorePin, { restorePin = it.filter(Char::isDigit).take(12) },
                        label = { Text("管理密码") }, visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (restorePin == managerPin) {
                        store.save(currentSettings())
                        showRestoreAuth = false
                        restoreLauncher.launch(arrayOf("application/json", "text/plain"))
                    } else Toast.makeText(activity, "管理密码错误", Toast.LENGTH_SHORT).show()
                }) { Text("验证并选择备份") }
            },
            dismissButton = { TextButton(onClick = { showRestoreAuth = false }) { Text("取消") } }
        )
    }

    if (showPrinterPicker) {
        AlertDialog(
            onDismissRequest = { showPrinterPicker = false },
            title = { Text("选择默认蓝牙打印机") },
            text = {
                if (pairedDevices.isEmpty()) {
                    Text("未找到已配对设备，请先在系统蓝牙中配对打印机。")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pairedDevices.forEach { device ->
                            OutlinedButton(
                                onClick = {
                                    printerName = device.name
                                    printerAddress = device.address
                                    showPrinterPicker = false
                                    Toast.makeText(activity, "已选择默认打印机，请点击保存全部设置", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("${device.name}\n${device.address}", textAlign = TextAlign.Center) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPrinterPicker = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun SettingsSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.padding(4.dp))
                Column {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                }
            }
            content()
        }
    }
}
