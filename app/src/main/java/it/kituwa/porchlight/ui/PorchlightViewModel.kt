package it.kituwa.porchlight.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.kituwa.porchlight.data.AuthKind
import it.kituwa.porchlight.data.SnapshotStore
import it.kituwa.porchlight.data.Server
import it.kituwa.porchlight.data.ServerSnapshot
import it.kituwa.porchlight.data.ServerStore
import it.kituwa.porchlight.data.ServerType
import it.kituwa.porchlight.data.PorchlightClient
import it.kituwa.porchlight.work.RefreshWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class OverviewState(
    val servers: List<Server> = emptyList(),
    val snapshots: Map<String, ServerSnapshot> = emptyMap(),
    val refreshing: Boolean = false,
) {
    val problemCount: Int
        get() = servers.sumOf { (snapshots[it.id]?.issues?.size ?: 0) }

    val summary: Summary
        get() = summarize(servers, snapshots)
}

class PorchlightViewModel(app: Application) : AndroidViewModel(app) {

    private val serverStore = ServerStore(app)
    private val snapshotStore = SnapshotStore(app)
    private val client = PorchlightClient(serverStore, snapshotStore)

    private val refreshing = MutableStateFlow(false)

    val state: StateFlow<OverviewState> = combine(
        serverStore.servers,
        snapshotStore.snapshots,
        refreshing,
    ) { servers, snapshots, isRefreshing ->
        OverviewState(servers, snapshots, isRefreshing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            runCatching { client.refreshAll() }
            refreshing.value = false
        }
    }

    fun save(
        existingId: String?,
        name: String,
        type: ServerType,
        url: String,
        authKind: AuthKind,
        username: String,
        secret: String,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val normalized = runCatching { HttpNormalizer.normalize(url) }.getOrNull()
            if (normalized == null) {
                onResult("That does not look like a valid address. Try https://your-server:3000")
                return@launch
            }
            serverStore.upsert(
                server = Server(
                    id = existingId ?: UUID.randomUUID().toString(),
                    name = name.trim().ifEmpty { normalized },
                    type = type,
                    baseUrl = normalized,
                    authKind = authKind,
                    username = username.trim().ifEmpty { null },
                ),
                plainSecret = secret,
            )
            RefreshWorker.schedule(getApplication())
            refresh()
            onResult(null)
        }
    }

    fun testConnection(
        url: String,
        type: ServerType,
        authKind: AuthKind,
        username: String,
        secret: String,
        onResult: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val normalized = runCatching { HttpNormalizer.normalize(url) }.getOrNull()
            if (normalized == null) {
                onResult("That does not look like a valid URL")
                return@launch
            }
            val probe = Server(
                id = "probe",
                name = "probe",
                type = type,
                baseUrl = normalized,
                authKind = authKind,
                username = username.trim().ifEmpty { null },
                secret = secret,
            )
            val message = runCatching { client.refresh(probe, persist = false, secretOverride = secret) }.fold(
                onSuccess = { snapshot ->
                    when (snapshot.reachability) {
                        it.kituwa.porchlight.data.Reachability.REACHABLE ->
                            "Connected — ${snapshot.details.joinToString(", ") { "${it.first} ${it.second}" }}"
                        it.kituwa.porchlight.data.Reachability.AUTH_FAILED ->
                            "Reached the server, but authentication was rejected"
                        else -> snapshot.message ?: "Could not get a status"
                    }
                },
                onFailure = { it.message ?: "Connection failed" },
            )
            onResult(message)
        }
    }

    fun delete(server: Server) {
        viewModelScope.launch {
            serverStore.delete(server.id)
            snapshotStore.clear(server.id)
        }
    }
}

object HttpNormalizer {
    fun normalize(raw: String): String =
        it.kituwa.porchlight.data.Http().normalizeUrl(raw)
}
