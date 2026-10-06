package com.stagedock.app.install

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class InstallRegistry(context: Context) {

    private val prefs = context.getSharedPreferences("installs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): Map<String, List<String>> =
        prefs.getString(KEY, null)
            ?.let { runCatching { json.decodeFromString<Map<String, List<String>>>(it) }.getOrNull() }
            ?: emptyMap()

    fun save(map: Map<String, List<String>>) {
        prefs.edit().putString(KEY, json.encodeToString(map.filterValues { it.isNotEmpty() })).apply()
    }

    private companion object {
        const val KEY = "by_stage_id"
    }
}
