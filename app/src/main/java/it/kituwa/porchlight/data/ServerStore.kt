package it.kituwa.porchlight.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "porchlight")

class ServerStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serversKey = stringPreferencesKey("servers")

    val servers: Flow<List<Server>> = context.dataStore.data.map { prefs ->
        val raw = prefs[serversKey] ?: return@map emptyList()
        runCatching { json.decodeFromString(ListSerializer(Server.serializer()), raw) }.getOrDefault(emptyList())
    }

    /**
     * [plainSecret] is passed separately rather than read off [server.secret] so
     * that a caller can never accidentally hand us an already-encrypted value and
     * have it encrypted a second time, which would destroy the stored credential.
     * An empty value keeps whatever is already stored, so renaming a server does
     * not require retyping its token.
     */
    suspend fun upsert(server: Server, plainSecret: String? = null) {
        context.dataStore.edit { prefs ->
            val current = current(prefs[serversKey]).toMutableList()
            val index = current.indexOfFirst { it.id == server.id }
            val existing = current.getOrNull(index)
            val secret = when {
                plainSecret != null && plainSecret.isNotEmpty() -> encryptSecret(plainSecret)
                plainSecret == null -> existing?.secret.orEmpty()
                else -> existing?.secret.orEmpty()
            }
            val stored = server.copy(secret = secret)
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

    /**
     * Read-only: this used to go through `edit {}`, which takes the DataStore
     * write lock and bumps the data version, so refreshing N servers serialised
     * N pointless write transactions against the very flow that emits the list.
     */
    suspend fun secretState(id: String): SecretState {
        val stored = context.dataStore.data.first()[serversKey]
            ?.let { raw -> current(raw).firstOrNull { it.id == id } }
            ?.secret
            .orEmpty()
        if (stored.isEmpty()) return SecretState.Absent
        val plain = runCatching { decryptSecret(stored) }.getOrNull()
        return if (plain == null) SecretState.Undecryptable else SecretState.Value(plain)
    }

    private fun current(raw: String?): List<Server> {
        if (raw == null) return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(Server.serializer()), raw) }.getOrDefault(emptyList())
    }

    private fun encryptSecret(plain: String): String = if (plain.isEmpty()) "" else Crypto.encrypt(plain)

    private fun decryptSecret(payload: String): String =
        if (payload.isEmpty()) "" else Crypto.decrypt(payload)
}
