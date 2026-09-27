package it.kituwa.porchlight.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class PorchlightClient(
    private val readSecret: suspend (String) -> String?,
    private val writeSnapshot: suspend (ServerSnapshot) -> Unit,
    private val readServers: suspend () -> List<Server>,
    private val readSnapshot: suspend (String) -> ServerSnapshot? = { null },
) {

    constructor(serverStore: ServerStore, snapshotStore: SnapshotStore) : this(
        readSecret = { id -> serverStore.secretFor(id) },
        writeSnapshot = { snapshot -> snapshotStore.put(snapshot) },
        readServers = { serverStore.servers.first().filter { it.enabled } },
        readSnapshot = { id -> snapshotStore.get(id) },
    )

    suspend fun refreshAll(): List<ServerSnapshot> {
        val servers = readServers().filter { it.enabled }
        return coroutineScope {
            servers.map { async { refresh(it) } }.awaitAll()
        }
    }

    /**
     * [secretOverride] carries plaintext credentials for a server that has not been
     * saved yet, which is what the Test connection button uses. Saved servers keep
     * their secret encrypted in the store, so it is always read back from there.
     */
    suspend fun refresh(
        server: Server,
        persist: Boolean = true,
        secretOverride: String? = null,
    ): ServerSnapshot {
        val secret = secretOverride ?: readSecret(server.id).orEmpty()
        val snapshot = try {
            when (server.type) {
                ServerType.GRAFANA -> GrafanaClient(Http()).fetch(server, secret)
                ServerType.PORTAINER -> PortainerClient(Http()).fetch(server, secret)
            }
        } catch (error: ReachabilityException) {
            unreachable(server, error.reachability, error.message)
        } catch (error: HttpException) {
            val reachability =
                if (error.status == 401 || error.status == 403) Reachability.AUTH_FAILED
                else Reachability.REACHABLE
            unreachable(server, reachability, "Server responded with HTTP ${error.status}")
        } catch (error: Exception) {
            unreachable(server, Reachability.UNREACHABLE, describe(error))
        }

        val now = System.currentTimeMillis()
        val withHonestTimestamps = snapshot.copy(
            lastSuccessAt = when {
                snapshot.reachability == Reachability.REACHABLE -> snapshot.observedAt
                else -> readSnapshot(server.id)?.lastSuccessAt
            },
        )
        if (persist) writeSnapshot(withHonestTimestamps)
        return withHonestTimestamps
    }

    /**
     * OkHttp exception messages are written for developers ("Failed to connect to
     * /10.0.2.2:3300"), which tells a homelab user nothing about what to change.
     */
    internal fun describe(error: Throwable): String = when (error) {
        is SocketTimeoutException ->
            "The server did not respond in time. Check the address and that it is still running."
        is UnknownHostException ->
            "That hostname could not be resolved. Check the address, or use the server's IP."
        is SSLException ->
            "The TLS certificate could not be verified. Try http:// instead if the server sits " +
                "behind a reverse proxy with a self-signed certificate."
        is ConnectException, is NoRouteToHostException, is PortUnreachableException ->
            "Could not open a connection. Check the port number and that the server is reachable " +
                "from this device."
        else -> error.message?.takeIf { it.isNotBlank() } ?: "Could not reach the server."
    }

    private fun unreachable(server: Server, reachability: Reachability, message: String) =
        ServerSnapshot(
            serverId = server.id,
            reachability = reachability,
            observedAt = System.currentTimeMillis(),
            message = message,
        )
}
