package it.kituwa.stackmate.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.snapshotStore by preferencesDataStore(name = "stackmate_snapshots")

class SnapshotStore(context: Context) {

    private val store = context.snapshotStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val snapshots: Flow<Map<String, ServerSnapshot>> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { decode(it[key]) }

    suspend fun put(snapshot: ServerSnapshot) {
        store.edit { prefs ->
            val current = decode(prefs[key]).toMutableMap()
            current[snapshot.serverId] = snapshot
            prefs[key] = json.encodeToString(MapSerializer(String.serializer(), ServerSnapshot.serializer()), current)
        }
    }

    suspend fun clear(id: String) {
        store.edit { prefs ->
            val current = decode(prefs[key]).toMutableMap()
            current.remove(id)
            prefs[key] = json.encodeToString(MapSerializer(String.serializer(), ServerSnapshot.serializer()), current)
        }
    }

    suspend fun all(): Map<String, ServerSnapshot> = decode(store.data.first()[key])

    suspend fun get(id: String): ServerSnapshot? = all()[id]

    private fun decode(raw: String?): Map<String, ServerSnapshot> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString(MapSerializer(String.serializer(), ServerSnapshot.serializer()), raw) }
            .getOrDefault(emptyMap())
    }

    private companion object {
        val key = stringPreferencesKey("snapshots")
    }
}
