package com.pandian.tobacco

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.File

data class AppSettings(
    val shopName: String = "烟收宝",
    val phone: String = "",
    val address: String = "",
    val receiptTitle: String = "烟草交易小票",
    val receiptFooter: String = "谢谢惠顾，请核对小票",
    val paperWidth: Int = 58,
    val defaultPrinterName: String = "",
    val defaultPrinterAddress: String = "",
    val managerPin: String = ""
)

class AppSettingsStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        shopName = preferences.getString("shopName", "烟收宝") ?: "烟收宝",
        phone = preferences.getString("phone", "") ?: "",
        address = preferences.getString("address", "") ?: "",
        receiptTitle = preferences.getString("receiptTitle", "烟草交易小票") ?: "烟草交易小票",
        receiptFooter = preferences.getString("receiptFooter", "谢谢惠顾，请核对小票") ?: "谢谢惠顾，请核对小票",
        paperWidth = preferences.getInt("paperWidth", 58),
        defaultPrinterName = preferences.getString("defaultPrinterName", "") ?: "",
        defaultPrinterAddress = preferences.getString("defaultPrinterAddress", "") ?: "",
        managerPin = preferences.getString("managerPin", "") ?: ""
    )

    fun save(settings: AppSettings) {
        preferences.edit()
            .putString("shopName", settings.shopName)
            .putString("phone", settings.phone)
            .putString("address", settings.address)
            .putString("receiptTitle", settings.receiptTitle)
            .putString("receiptFooter", settings.receiptFooter)
            .putInt("paperWidth", settings.paperWidth)
            .putString("defaultPrinterName", settings.defaultPrinterName)
            .putString("defaultPrinterAddress", settings.defaultPrinterAddress)
            .putString("managerPin", settings.managerPin)
            .apply()
    }

    fun savePrinter(name: String, address: String) {
        val current = load()
        save(current.copy(defaultPrinterName = name, defaultPrinterAddress = address))
    }
}

object AppDataBackup {
    fun export(context: Context, target: Uri): Result<Unit> = runCatching {
        val tobacco = context.getSharedPreferences("tobacco_ledger", Context.MODE_PRIVATE)
        val accounting = context.getSharedPreferences("accounting_ledger", Context.MODE_PRIVATE)
        val customerLedger = context.getSharedPreferences("customer_ledger", Context.MODE_PRIVATE)
        val settings = AppSettingsStore(context).load()
        val root = JSONObject().apply {
            put("formatVersion", 1)
            put("exportedAt", System.currentTimeMillis())
            put("products", tobacco.getString("products", "[]"))
            put("orders", tobacco.getString("orders", "[]"))
            put("intakes", tobacco.getString("intakes", "[]"))
            put("people", accounting.getString("people", "[]"))
            put("entries", accounting.getString("entries", "[]"))
            put("customers", customerLedger.getString("customers", "[]"))
            put("images", JSONObject().apply {
                put("product_images", encodeDirectory(File(context.filesDir, "product_images")))
                put("person_images", encodeDirectory(File(context.filesDir, "person_images")))
                put("customer_images", encodeDirectory(File(context.filesDir, "customer_images")))
            })
            put("settings", JSONObject().apply {
                put("shopName", settings.shopName)
                put("phone", settings.phone)
                put("address", settings.address)
                put("receiptTitle", settings.receiptTitle)
                put("receiptFooter", settings.receiptFooter)
                put("paperWidth", settings.paperWidth)
                put("defaultPrinterName", settings.defaultPrinterName)
                put("defaultPrinterAddress", settings.defaultPrinterAddress)
            })
        }
        context.contentResolver.openOutputStream(target)?.bufferedWriter()?.use { it.write(root.toString(2)) }
            ?: error("无法创建备份文件")
    }

    fun restore(context: Context, source: Uri): Result<Unit> = runCatching {
        val text = context.contentResolver.openInputStream(source)?.bufferedReader()?.use { it.readText() }
            ?: error("无法读取备份文件")
        val root = JSONObject(text)
        require(root.optInt("formatVersion") == 1) { "不支持的备份版本" }
        context.getSharedPreferences("tobacco_ledger", Context.MODE_PRIVATE).edit()
            .putString("products", root.getString("products"))
            .putString("orders", root.getString("orders"))
            .putString("intakes", root.optString("intakes", "[]"))
            .apply()
        context.getSharedPreferences("accounting_ledger", Context.MODE_PRIVATE).edit()
            .putString("people", root.getString("people"))
            .putString("entries", root.getString("entries"))
            .apply()
        context.getSharedPreferences("customer_ledger", Context.MODE_PRIVATE).edit()
            .putString("customers", root.optString("customers", "[]"))
            .apply()
        root.optJSONObject("images")?.let { images ->
            restoreDirectory(File(context.filesDir, "product_images"), images.optJSONObject("product_images"))
            restoreDirectory(File(context.filesDir, "person_images"), images.optJSONObject("person_images"))
            restoreDirectory(File(context.filesDir, "customer_images"), images.optJSONObject("customer_images"))
        }
        val json = root.getJSONObject("settings")
        AppSettingsStore(context).save(
            AppSettings(
                json.optString("shopName", "烟收宝"), json.optString("phone"), json.optString("address"),
                json.optString("receiptTitle", "烟草交易小票"),
                json.optString("receiptFooter", "谢谢惠顾，请核对小票"),
                json.optInt("paperWidth", 58), json.optString("defaultPrinterName"), json.optString("defaultPrinterAddress"),
                AppSettingsStore(context).load().managerPin
            )
        )
    }

    private fun encodeDirectory(directory: File): JSONObject = JSONObject().apply {
        directory.listFiles()?.filter { it.isFile }?.forEach { file ->
            put(file.name, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
        }
    }

    private fun restoreDirectory(directory: File, json: JSONObject?) {
        if (json == null) return
        directory.mkdirs()
        json.keys().forEach { name ->
            val safeName = name.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            File(directory, safeName).writeBytes(Base64.decode(json.getString(name), Base64.DEFAULT))
        }
    }
}
