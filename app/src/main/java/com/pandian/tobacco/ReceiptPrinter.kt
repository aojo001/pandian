package com.pandian.tobacco

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.UUID

data class PrinterDevice(val name: String, val address: String)

data class PrintLayoutConfig(
    val paperWidthCells: Int,
    val nameWidth: Int,
    val priceWidth: Int,
    val quantityWidth: Int,
    val subtotalWidth: Int
) {
    val totalWidth: Int get() = nameWidth + priceWidth + quantityWidth + subtotalWidth

    companion object {
        val MM58 = PrintLayoutConfig(
            paperWidthCells = 32,
            nameWidth = 10,
            priceWidth = 7,
            quantityWidth = 6,
            subtotalWidth = 9
        )
        val MM80 = PrintLayoutConfig(
            paperWidthCells = 48,
            nameWidth = 18,
            priceWidth = 10,
            quantityWidth = 7,
            subtotalWidth = 13
        )
        fun forPaperWidth(paperWidth: Int): PrintLayoutConfig = if (paperWidth >= 80) MM80 else MM58
    }
}

object ReceiptPrinter {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun needsBluetoothPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

    private val bluetoothPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN
    )

    @Composable
    fun rememberBluetoothPermissionRequest(): (onResult: (Boolean) -> Unit) -> Unit {
        val context = LocalContext.current
        var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            pending?.invoke(grants.values.all { it })
            pending = null
        }
        return { onResult ->
            if (needsBluetoothPermission(context)) {
                pending = onResult
                launcher.launch(bluetoothPermissions)
            } else {
                onResult(true)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<PrinterDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return adapter.bondedDevices
            .map { PrinterDevice(it.name ?: "蓝牙设备", it.address) }
            .sortedBy { it.name }
    }

    @SuppressLint("MissingPermission")
    fun printBluetooth(
        context: Context,
        address: String,
        order: TradeOrder,
        callback: (Result<Unit>) -> Unit
    ) {
        Thread {
            val result = runCatching {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: error("此设备不支持蓝牙")
                if (!adapter.isEnabled) error("请先打开手机蓝牙")
                val device: BluetoothDevice = adapter.getRemoteDevice(address)
                adapter.cancelDiscovery()
                device.createRfcommSocketToServiceRecord(sppUuid).use { socket ->
                    socket.connect()
                    socket.outputStream.use { output ->
                        output.write(byteArrayOf(0x1B, 0x40))
                        output.write(receiptText(order, AppSettingsStore(context).load()).toByteArray(charset("GBK")))
                        output.write(byteArrayOf(0x0A, 0x0A, 0x0A))
                        output.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
                        output.flush()
                    }
                }
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }

    fun printWithSystem(activity: Activity, order: TradeOrder) {
        val settings = AppSettingsStore(activity).load()
        val webView = WebView(activity)
        webView.settings.defaultTextEncodingName = "UTF-8"
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                manager.print(
                    "烟草小票-${order.id.take(8)}",
                    view.createPrintDocumentAdapter("交易小票"),
                    PrintAttributes.Builder().build()
                )
            }
        }
        webView.loadDataWithBaseURL(null, receiptHtml(order, settings), "text/html", "UTF-8", null)
    }

    private fun receiptText(order: TradeOrder, settings: AppSettings): String = buildString {
        val config = PrintLayoutConfig.forPaperWidth(settings.paperWidth)
        val width = config.paperWidthCells
        val separator = "-".repeat(width)
        appendLine(centerText(settings.receiptTitle, width))
        appendLine(centerText(settings.shopName, width))
        appendLine(separator)
        if (settings.phone.isNotBlank()) appendLine("电话: ${settings.phone}")
        if (settings.address.isNotBlank()) appendLine("地址: ${settings.address}")
        appendLine("单号: ${order.id.take(8).uppercase()}")
        appendLine("时间: ${order.displayTime}")
        appendLine("业务: ${order.tradeType.title}")
        if (order.customerName.isNotBlank()) appendLine("姓名: ${order.customerName}")
        appendLine(separator)
        appendLine(formatHeader(config))
        order.lines.forEach { line ->
            formatOrderLine(line, config).forEach { appendLine(it) }
        }
        appendLine(separator)
        appendLine("合计数量: ${order.totalQuantity}")
        appendLine("应付金额: ￥${money(order.total)}")
        appendLine(separator)
        appendLine(centerText(settings.receiptFooter, width))
    }

    private fun formatHeader(config: PrintLayoutConfig): String =
        padRight("商品", config.nameWidth) +
            padLeft("单价", config.priceWidth) +
            padLeft("数量", config.quantityWidth) +
            padLeft("小计", config.subtotalWidth)

    private fun formatOrderLine(line: CartLine, config: PrintLayoutConfig): List<String> {
        val price = padLeftOverflow(money(line.unitPrice), config.priceWidth)
        val quantity = padLeftOverflow("${line.quantity}${line.product.unit}", config.quantityWidth)
        val subtotal = padLeftOverflow(money(line.subtotal), config.subtotalWidth)
        val numberRow = " ".repeat(config.nameWidth) + price + quantity + subtotal
        return if (displayWidth(line.product.name) <= config.nameWidth) {
            listOf(padRight(line.product.name, config.nameWidth) + price + quantity + subtotal)
        } else {
            listOf(truncateByWidth(line.product.name, config.paperWidthCells), numberRow)
        }
    }

    private fun displayWidth(text: String): Int =
        text.fold(0) { acc, ch -> acc + charWidth(ch) }

    private fun charWidth(ch: Char): Int =
        when (ch.code) {
            in 0x2E80..0x9FFF, in 0xF900..0xFAFF, in 0xFF00..0xFF60 -> 2
            else -> 1
        }

    private fun padRight(text: String, width: Int): String {
        val w = displayWidth(text)
        if (w >= width) return truncateByWidth(text, width)
        return text + " ".repeat(width - w)
    }

    private fun padLeft(text: String, width: Int): String {
        val w = displayWidth(text)
        if (w >= width) return truncateByWidth(text, width)
        return " ".repeat(width - w) + text
    }

    private fun padLeftOverflow(text: String, width: Int): String {
        val w = displayWidth(text)
        if (w >= width) return text
        return " ".repeat(width - w) + text
    }

    private fun truncateByWidth(text: String, width: Int): String {
        var w = 0
        val sb = StringBuilder()
        for (ch in text) {
            val cw = charWidth(ch)
            if (w + cw > width) break
            sb.append(ch)
            w += cw
        }
        return sb.toString()
    }

    private fun centerText(text: String, width: Int): String {
        val w = displayWidth(text)
        if (w >= width) return text
        val left = (width - w) / 2
        val right = width - w - left
        return " ".repeat(left) + text + " ".repeat(right)
    }

    private fun receiptHtml(order: TradeOrder, settings: AppSettings): String {
        val is80 = settings.paperWidth >= 80
        val cssWidth = if (is80) "400px" else "280px"
        val nameCol = if (is80) "40%" else "35%"
        val priceCol = if (is80) "20%" else "22%"
        val qtyCol = if (is80) "15%" else "18%"
        val subtotalCol = if (is80) "25%" else "25%"
        return """
        <!doctype html><html><head><meta charset="utf-8">
        <style>
        body{font-family:sans-serif;width:$cssWidth;margin:20px auto;color:#111;box-sizing:border-box;padding:0 4px}
        h2{text-align:center;margin:4px 0}.shop{text-align:center}.meta{line-height:1.7}
        .line{border-top:1px dashed #555;margin:10px 0}
        table{width:100%;border-collapse:collapse;table-layout:fixed}
        th,td{padding:4px 2px;text-align:right;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
        th:first-child,td:first-child{text-align:left}
        .c-name{width:$nameCol}.c-price{width:$priceCol}.c-qty{width:$qtyCol}.c-subtotal{width:$subtotalCol}
        .total{font-size:20px;font-weight:bold;text-align:right}
        .footer{text-align:center}
        </style>
        </head><body><h2>${escape(settings.receiptTitle)}</h2><div class="shop">${escape(settings.shopName)}</div>
        ${if (settings.phone.isNotBlank()) "<div style=\"text-align:center\">${escape(settings.phone)}</div>" else ""}
        ${if (settings.address.isNotBlank()) "<div style=\"text-align:center\">${escape(settings.address)}</div>" else ""}<div class="line"></div>
        <div class="meta">单号：${order.id.take(8).uppercase()}<br>时间：${order.displayTime}<br>
        业务：${order.tradeType.title}<br>
        ${if (order.customerName.isNotBlank()) "姓名：${escape(order.customerName)}<br>" else ""}</div>
        <div class="line"></div><table>
        <tr><th class="c-name">商品</th><th class="c-price">单价</th><th class="c-qty">数量</th><th class="c-subtotal">小计</th></tr>
        ${order.lines.joinToString("") { line ->
            "<tr><td class=\"c-name\">${escape(line.product.name)}</td><td class=\"c-price\">${money(line.unitPrice)}</td><td class=\"c-qty\">${line.quantity}${escape(line.product.unit)}</td><td class=\"c-subtotal\">${money(line.subtotal)}</td></tr>"
        }}
        </table><div class="line"></div><div>合计数量：${order.totalQuantity}</div>
        <div class="total">应付金额：￥${money(order.total)}</div><div class="line"></div>
        <p class="footer">${escape(settings.receiptFooter)}</p></body></html>
        """.trimIndent()
    }

    private fun escape(value: String) = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

@Composable
fun AutoPrint(
    activity: Activity,
    order: TradeOrder,
    onDone: () -> Unit,
    onNoPrinter: () -> Unit
) {
    val settings = remember(order) { AppSettingsStore(activity).load() }
    val requestBluetooth = ReceiptPrinter.rememberBluetoothPermissionRequest()

    LaunchedEffect(order) {
        val address = settings.defaultPrinterAddress
        if (address.isBlank()) {
            onNoPrinter()
            return@LaunchedEffect
        }
        requestBluetooth { granted ->
            if (!granted) {
                Toast.makeText(activity, "未授权蓝牙权限，请到设置中授权后再自动打印", Toast.LENGTH_LONG).show()
                onDone()
                return@requestBluetooth
            }
            ReceiptPrinter.printBluetooth(activity, address, order) { result ->
                result.onSuccess { Toast.makeText(activity, "小票打印成功", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(activity, "打印失败：${it.message ?: "请检查打印机"}", Toast.LENGTH_LONG).show() }
                onDone()
            }
        }
    }
}
