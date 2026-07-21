package com.cybertkr.suboverlay

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "suboverlay_settings")

class SettingsStore(private val context: Context) {
    private fun key(title: String) = longPreferencesKey("offset_$title")
    suspend fun getOffset(title: String): Long =
        context.dataStore.data.first()[key(title)] ?: 0L
    suspend fun setOffset(title: String, ms: Long) {
        context.dataStore.edit { it[key(title)] = ms }
    }

    suspend fun getRecent(): List<Pair<String, String>> {
        val raw = context.dataStore.data.first()[RECENT] ?: return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }.mapNotNull {
            val i = it.indexOf('\t')
            if (i < 0) null else it.substring(0, i) to it.substring(i + 1)
        }
    }

    suspend fun addRecent(uri: String, name: String) {
        val next = (listOf(uri to name) + getRecent().filter { it.first != uri }).take(5)
        context.dataStore.edit { p ->
            p[RECENT] = next.joinToString("\n") { "${it.first}\t${it.second}" }
        }
    }

    suspend fun removeRecent(uri: String) {
        val next = getRecent().filter { it.first != uri }
        context.dataStore.edit { p ->
            p[RECENT] = next.joinToString("\n") { "${it.first}\t${it.second}" }
        }
    }

    private companion object {
        val RECENT = stringPreferencesKey("recent_srts")
    }
}
