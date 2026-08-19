package com.pandian.tobacco

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class LedgerStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = context.getSharedPreferences("tobacco_ledger", Context.MODE_PRIVATE)

    fun loadProducts(): List<Product> {
        val raw = preferences.getString("products", null) ?: return defaultProducts()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                array.getJSONObject(index).run {
                    Product(
                        getString("id"), getString("name"), getDouble("referencePrice"),
                        getDouble("receivePrice"), getDouble("retailPrice"), getDouble("wholesalePrice"),
                        optString("imagePath").takeIf { it.isNotBlank() },
                        optString("category", "卷烟").ifBlank { "卷烟" },
                        optString("unit", "条").ifBlank { "条" },
                        optString("barcode")
                    )
                }
            }
        }.getOrElse { defaultProducts() }
    }

    fun saveProducts(products: List<Product>) {
        val array = JSONArray()
        products.forEach { product ->
            array.put(JSONObject().apply {
                put("id", product.id)
                put("name", product.name)
                put("referencePrice", product.referencePrice)
                put("receivePrice", product.receivePrice)
                put("retailPrice", product.retailPrice)
                put("wholesalePrice", product.wholesalePrice)
                put("imagePath", product.imagePath ?: "")
                put("category", product.category)
                put("unit", product.unit)
                put("barcode", product.barcode)
            })
        }
        preferences.edit().putString("products", array.toString()).apply()
    }

    fun loadCategories(products: List<Product>): List<String> {
        val saved = runCatching {
            val array = JSONArray(preferences.getString("categoryOrder", "[]"))
            List(array.length()) { array.getString(it) }
        }.getOrDefault(emptyList())
        return (saved + DefaultProductCategories + products.map { it.category.ifBlank { "未分类" } })
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }

    fun saveCategories(categories: List<String>) {
        preferences.edit().putString("categoryOrder", JSONArray(categories).toString()).apply()
    }

    fun loadOrders(): List<TradeOrder> {
        val raw = preferences.getString("orders", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> orderFromJson(array.getJSONObject(index)) }
        }.getOrDefault(emptyList())
    }

    fun addOrder(order: TradeOrder) {
        val orders = listOf(order) + loadOrders()
        val array = JSONArray()
        orders.take(200).forEach { array.put(orderToJson(it)) }
        preferences.edit().putString("orders", array.toString()).apply()
    }

    fun loadIntakes(): List<IntakeRecord> {
        val raw = preferences.getString("intakes", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> intakeFromJson(array.getJSONObject(index)) }
        }.getOrDefault(emptyList()).sortedByDescending { it.createdAt }
    }

    fun addIntake(lines: List<IntakeLine>): IntakeRecord {
        val record = IntakeRecord(UUID.randomUUID().toString(), System.currentTimeMillis(), lines)
        saveIntakes(listOf(record) + loadIntakes())
        return record
    }

    fun deleteIntake(id: String) {
        saveIntakes(loadIntakes().filterNot { it.id == id })
    }

    fun newOrder(
        tradeType: TradeType,
        customerName: String,
        lines: List<CartLine>
    ) = TradeOrder(UUID.randomUUID().toString(), System.currentTimeMillis(), tradeType, CustomerType.RETAIL, customerName, lines)

    fun saveProductImage(productId: String, source: Uri): String? =
        ImageStorage.saveNormalized(appContext, source, "product_images", productId)

    private fun orderToJson(order: TradeOrder) = JSONObject().apply {
        put("id", order.id)
        put("createdAt", order.createdAt)
        put("tradeType", order.tradeType.name)
        put("customerType", order.customerType.name)
        put("customerName", order.customerName)
        put("lines", JSONArray().apply {
            order.lines.forEach { line ->
                put(JSONObject().apply {
                    put("productId", line.product.id)
                    put("productName", line.product.name)
                    put("referencePrice", line.product.referencePrice)
                    put("receivePrice", line.product.receivePrice)
                    put("retailPrice", line.product.retailPrice)
                    put("wholesalePrice", line.product.wholesalePrice)
                    put("imagePath", line.product.imagePath ?: "")
                    put("category", line.product.category)
                    put("unit", line.product.unit)
                    put("barcode", line.product.barcode)
                    put("unitPrice", line.unitPrice)
                    put("quantity", line.quantity)
                })
            }
        })
    }

    private fun orderFromJson(json: JSONObject): TradeOrder {
        val linesJson = json.getJSONArray("lines")
        val lines = List(linesJson.length()) { index ->
            linesJson.getJSONObject(index).run {
                val product = Product(
                    getString("productId"), getString("productName"), getDouble("referencePrice"),
                    getDouble("receivePrice"), getDouble("retailPrice"), getDouble("wholesalePrice"),
                    optString("imagePath").takeIf { it.isNotBlank() },
                    optString("category", "卷烟").ifBlank { "卷烟" },
                    optString("unit", "条").ifBlank { "条" },
                    optString("barcode")
                )
                CartLine(product, getDouble("unitPrice"), getInt("quantity"))
            }
        }
        return TradeOrder(
            json.getString("id"), json.getLong("createdAt"),
            TradeType.valueOf(json.getString("tradeType")),
            CustomerType.valueOf(json.getString("customerType")),
            json.optString("customerName"), lines
        )
    }

    private fun saveIntakes(records: List<IntakeRecord>) {
        val array = JSONArray()
        records.take(300).forEach { record ->
            array.put(JSONObject().apply {
                put("id", record.id)
                put("createdAt", record.createdAt)
                put("lines", JSONArray().apply {
                    record.lines.forEach { line ->
                        put(JSONObject().apply {
                            put("productId", line.product.id)
                            put("productName", line.product.name)
                            put("referencePrice", line.product.referencePrice)
                            put("receivePrice", line.product.receivePrice)
                            put("retailPrice", line.product.retailPrice)
                            put("wholesalePrice", line.product.wholesalePrice)
                            put("imagePath", line.product.imagePath ?: "")
                            put("category", line.product.category)
                            put("unit", line.product.unit)
                            put("barcode", line.product.barcode)
                            put("quantity", line.quantity)
                        })
                    }
                })
            })
        }
        preferences.edit().putString("intakes", array.toString()).apply()
    }

    private fun intakeFromJson(json: JSONObject): IntakeRecord {
        val linesJson = json.getJSONArray("lines")
        val lines = List(linesJson.length()) { index ->
            linesJson.getJSONObject(index).run {
                IntakeLine(
                    Product(
                        getString("productId"), getString("productName"), getDouble("referencePrice"),
                        getDouble("receivePrice"), getDouble("retailPrice"), getDouble("wholesalePrice"),
                        optString("imagePath").takeIf { it.isNotBlank() },
                        optString("category", "卷烟").ifBlank { "卷烟" },
                        optString("unit", "条").ifBlank { "条" },
                        optString("barcode")
                    ),
                    getInt("quantity")
                )
            }
        }
        return IntakeRecord(json.getString("id"), json.getLong("createdAt"), lines)
    }

    private fun defaultProducts() = listOf(
        Product("red-nanjing", "红南京", 13.00, 10.50, 14.00, 13.50),
        Product("xuanhemen", "炫赫门", 18.00, 15.50, 20.00, 19.00),
        Product("yuxi", "玉溪", 23.00, 20.00, 25.00, 24.00),
        Product("liqun", "利群", 18.00, 15.00, 20.00, 19.00)
    )
}
