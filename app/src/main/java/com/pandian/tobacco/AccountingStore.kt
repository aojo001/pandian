package com.pandian.tobacco

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class MoneyDirection(val title: String) {
    INCOME("我收钱"), EXPENSE("我付钱")
}

data class AccountPerson(
    val id: String,
    val name: String,
    val note: String,
    val imagePath: String?,
    val createdAt: Long
)

data class AccountEntry(
    val id: String,
    val personId: String,
    val personName: String,
    val direction: MoneyDirection,
    val amount: Double,
    val note: String,
    val createdAt: Long
) {
    val displayTime: String
        get() = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(createdAt))
}

class AccountingStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("accounting_ledger", Context.MODE_PRIVATE)

    fun newId(): String = UUID.randomUUID().toString()

    fun loadPeople(): List<AccountPerson> = runCatching {
        val array = JSONArray(preferences.getString("people", "[]"))
        List(array.length()) { index ->
            array.getJSONObject(index).run {
                AccountPerson(
                    getString("id"), getString("name"), optString("note"),
                    optString("imagePath").takeIf { it.isNotBlank() }, getLong("createdAt")
                )
            }
        }
    }.getOrDefault(emptyList())

    fun savePerson(person: AccountPerson) {
        val people = loadPeople().toMutableList()
        val index = people.indexOfFirst { it.id == person.id }
        if (index >= 0) people[index] = person else people.add(person)
        savePeople(people)
    }

    fun savePersonImage(personId: String, source: Uri): String? =
        ImageStorage.saveNormalized(appContext, source, "person_images", personId)

    fun loadEntries(): List<AccountEntry> = runCatching {
        val array = JSONArray(preferences.getString("entries", "[]"))
        List(array.length()) { index ->
            array.getJSONObject(index).run {
                AccountEntry(
                    getString("id"), getString("personId"), getString("personName"),
                    MoneyDirection.valueOf(getString("direction")), getDouble("amount"),
                    optString("note"), getLong("createdAt")
                )
            }
        }
    }.getOrDefault(emptyList()).sortedByDescending { it.createdAt }

    fun addEntry(entry: AccountEntry) {
        saveEntries((listOf(entry) + loadEntries()).take(1000))
    }

    fun deleteEntry(id: String) {
        saveEntries(loadEntries().filterNot { it.id == id })
    }

    private fun savePeople(people: List<AccountPerson>) {
        val array = JSONArray()
        people.forEach { person ->
            array.put(JSONObject().apply {
                put("id", person.id)
                put("name", person.name)
                put("note", person.note)
                put("imagePath", person.imagePath ?: "")
                put("createdAt", person.createdAt)
            })
        }
        preferences.edit().putString("people", array.toString()).apply()
    }

    private fun saveEntries(entries: List<AccountEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("personId", entry.personId)
                put("personName", entry.personName)
                put("direction", entry.direction.name)
                put("amount", entry.amount)
                put("note", entry.note)
                put("createdAt", entry.createdAt)
            })
        }
        preferences.edit().putString("entries", array.toString()).apply()
    }
}
