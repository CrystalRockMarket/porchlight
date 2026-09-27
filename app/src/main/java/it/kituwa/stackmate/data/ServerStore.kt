package it.kituwa.stackmate.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "stackmate")

class ServerStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serversKey = stringPreferencesKey("servers")

    val servers: Flow<List<Server>> = context.dataStore.data.map { prefs ->
        val raw = prefs[serversKey] ?: return@map emptyList()
        runCatching { json.decodeFromString(ListSerializer(Server.serializer()), raw) }.getOrDefault(emptyList())
    }

    suspend fun upsert(server: Server) {
        context.dataStore.edit { prefs ->
            val current = current(prefs[serversKey]).toMutableList()
            val stored = server.copy(secret = encryptSecret(server.secret))
            val index = current.indexOfFirst { it.id == server.id }
            if (index >= 0) current[index] = stored else current.add(stored)
            prefs[serversKey] = json.encodeToString(ListSerializer(Server.serializer()), current)
        }
    }

    suspend fun delete(id: String) {
        context.dataStore.edit { prefs ->
            val remaining = current(prefs[serversKey]).filterNot { it.id == id }
            prefs[serversKey] = json.encodeToString(ListSerializer(Server.serializer()), remaining)
        }
    }

    suspend fun secretFor(id: String): String? {
        var value: String? = null
        context.dataStore.edit { prefs ->
            value = current(prefs[serversKey]).firstOrNull { it.id == id }
                ?.secret
                ?.let { runCatching { decryptSecret(it) }.getOrNull() }
        }
        return value
    }

    private fun current(raw: String?): List<Server> {
        if (raw == null) return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(Server.serializer()), raw) }.getOrDefault(emptyList())
    }

    private fun encryptSecret(plain: String): String = if (plain.isEmpty()) "" else Crypto.encrypt(plain)

    private fun decryptSecret(payload: String): String =
        if (payload.isEmpty()) "" else Crypto.decrypt(payload)
}
