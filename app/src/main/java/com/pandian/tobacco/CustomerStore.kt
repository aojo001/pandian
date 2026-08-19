package com.pandian.tobacco

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Customer(
    val id: String,
    val name: String,
    val phone: String,
    val note: String,
    val imagePath: String?,
    val createdAt: Long
)

class CustomerStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("customer_ledger", Context.MODE_PRIVATE)

    fun newCustomer() = Customer(
        id = UUID.randomUUID().toString(), name = "", phone = "", note = "",
        imagePath = null, createdAt = System.currentTimeMillis()
    )

    fun loadCustomers(): List<Customer> = runCatching {
        val array = JSONArray(preferences.getString("customers", "[]"))
        List(array.length()) { index ->
            array.getJSONObject(index).run {
                Customer(
                    getString("id"), getString("name"), optString("phone"), optString("note"),
                    optString("imagePath").takeIf { it.isNotBlank() }, optLong("createdAt", System.currentTimeMillis())
                )
            }
        }
    }.getOrDefault(emptyList()).sortedBy { it.createdAt }

    fun migrateLegacyPeople(legacyPeople: List<AccountPerson>): List<Customer> {
        val merged = loadCustomers().toMutableList()
        var changed = false
        legacyPeople.forEach { person ->
            val index = merged.indexOfFirst {
                it.id == person.id || it.name.trim().equals(person.name.trim(), ignoreCase = true)
            }
            if (index < 0) {
                merged.add(Customer(person.id, person.name, "", person.note, person.imagePath, person.createdAt))
                changed = true
            } else {
                val current = merged[index]
                val updated = current.copy(
                    note = current.note.ifBlank { person.note },
                    imagePath = current.imagePath ?: person.imagePath
                )
                if (updated != current) {
                    merged[index] = updated
                    changed = true
                }
            }
        }
        if (changed) saveCustomers(merged)
        return merged.sortedBy { it.createdAt }
    }

    fun saveCustomers(customers: List<Customer>) {
        val array = JSONArray()
        customers.forEach { customer ->
            array.put(JSONObject().apply {
                put("id", customer.id)
                put("name", customer.name)
                put("phone", customer.phone)
                put("note", customer.note)
                put("imagePath", customer.imagePath ?: "")
                put("createdAt", customer.createdAt)
            })
        }
        preferences.edit().putString("customers", array.toString()).apply()
    }

    fun saveCustomerImage(customerId: String, source: Uri): String? =
        ImageStorage.saveNormalized(appContext, source, "customer_images", customerId)
}
