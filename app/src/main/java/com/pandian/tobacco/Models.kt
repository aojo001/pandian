package com.pandian.tobacco

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TradeType(val title: String, val priceLabel: String) {
    RECEIVE("收烟", "回收价"), BUY("卖烟", "卖价")
}

enum class CustomerType(val title: String) {
    RETAIL("散户"), WHOLESALE("大户")
}

data class Product(
    val id: String,
    val name: String,
    val referencePrice: Double,
    val receivePrice: Double,
    val retailPrice: Double,
    val wholesalePrice: Double,
    val imagePath: String? = null,
    val category: String = "卷烟",
    val unit: String = "条",
    val barcode: String = ""
)

data class CartLine(
    val product: Product,
    val unitPrice: Double,
    val quantity: Int
) {
    val subtotal: Double get() = unitPrice * quantity
}

data class IntakeLine(
    val product: Product,
    val quantity: Int
)

data class IntakeRecord(
    val id: String,
    val createdAt: Long,
    val lines: List<IntakeLine>
) {
    val totalQuantity: Int get() = lines.sumOf { it.quantity }
    val displayTime: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(createdAt))
}

data class TradeOrder(
    val id: String,
    val createdAt: Long,
    val tradeType: TradeType,
    val customerType: CustomerType,
    val customerName: String,
    val lines: List<CartLine>
) {
    val total: Double get() = lines.sumOf { it.subtotal }
    val totalQuantity: Int get() = lines.sumOf { it.quantity }
    val displayTime: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(createdAt))
}

fun money(value: Double) = "%.2f".format(Locale.CHINA, value)

fun normalizeBarcode(value: String): String {
    val compact = value.filterNot(Char::isWhitespace).trim()
    return if (compact.length > 3 && compact[0] == ']' && compact[1].isLetterOrDigit() && compact[2].isLetterOrDigit()) {
        compact.drop(3)
    } else compact
}
