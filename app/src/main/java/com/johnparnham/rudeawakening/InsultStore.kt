package com.johnparnham.rudeawakening

import android.content.Context
import org.json.JSONObject

/** Loads John's 91 insults from assets/insults.json, exactly as supplied. */
object InsultStore {
    fun load(context: Context): List<String> {
        return try {
            val text = context.assets.open("insults.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(text)
            val arr = obj.getJSONArray("insults")
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "").trim()
                if (s.isNotEmpty()) out.add(s)
            }
            out
        } catch (e: Exception) {
            // Fallback so the alarm still works if the asset is ever missing.
            listOf("Get the fuck up. Your life isn't going to live itself!")
        }
    }
}

/** Shuffled deck: no repeats until every insult has been used once. */
class InsultDeck(private val insults: List<String>) {
    private val order = insults.indices.shuffled().toMutableList()
    private var pos = 0

    fun next(): String {
        if (insults.isEmpty()) return "Get up."
        if (pos >= order.size) {
            order.clear()
            order.addAll(insults.indices.shuffled())
            pos = 0
        }
        return insults[order[pos++]]
    }
}
