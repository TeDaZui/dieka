package com.dieka.wallet.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal object VaultCodec {
    fun encode(snapshot: VaultSnapshot): String = JSONObject().apply {
        put("version", 2)
        put("cards", JSONArray().apply { snapshot.cards.forEach { put(CardCodec.encode(it)) } })
        put("trash", JSONArray().apply {
            snapshot.trash.forEach { entry ->
                put(JSONObject().apply {
                    put("id", entry.id); put("card", CardCodec.encode(entry.card))
                    put("deletedAt", entry.deletedAt); put("originalIndex", entry.originalIndex)
                    put("previousId", entry.previousId ?: JSONObject.NULL)
                    put("nextId", entry.nextId ?: JSONObject.NULL)
                })
            }
        })
    }.toString()

    fun decode(text: String): VaultSnapshot {
        val value = JSONTokener(text).nextValue()
        if (value is JSONArray) return VaultSnapshot(readCards(value))
        require(value is JSONObject && value.getInt("version") == 2) { "Unsupported vault format" }
        val cards = readCards(value.getJSONArray("cards"))
        val records = value.getJSONArray("trash")
        val trash = List(records.length()) { i ->
            val item = records.getJSONObject(i)
            TrashEntry(id = item.getString("id"), card = CardCodec.decode(item.getJSONObject("card")),
                deletedAt = item.getLong("deletedAt"), originalIndex = item.getInt("originalIndex"),
                previousId = if (item.isNull("previousId")) null else item.getString("previousId"),
                nextId = if (item.isNull("nextId")) null else item.getString("nextId"))
        }
        require(cards.map { it.id }.distinct().size == cards.size)
        require(trash.map { it.id }.distinct().size == trash.size)
        return VaultSnapshot(cards, trash)
    }

    private fun readCards(array: JSONArray) = List(array.length()) { CardCodec.decode(array.getJSONObject(it)) }
}
