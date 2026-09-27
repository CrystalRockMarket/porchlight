package it.kituwa.stackmate.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

class StackMateClient(
    private val serverStore: ServerStore,
    private val snapshotStore: SnapshotStore,
) {

    suspend fun refreshAll(): List<ServerSnapshot> {
        val servers = enabledServers()
        return coroutineScope {
            servers.map { async { refresh(it) } }.awaitAll()
        }
    }

    suspend fun refresh(server: Server, persist: Boolean = true): ServerSnapshot {
        val secret = serverStore.secretFor(server.id).orEmpty()
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
            unreachable(server, Reachability.UNREACHABLE, error.message ?: "Could not reach server")
        }

        if (persist) snapshotStore.put(snapshot)
        return snapshot
    }

    private fun unreachable(server: Server, reachability: Reachability, message: String) =
        ServerSnapshot(
            serverId = server.id,
            reachability = reachability,
            observedAt = System.currentTimeMillis(),
            message = message,
        )

    private suspend fun enabledServers(): List<Server> =
        serverStore.servers.first().filter { it.enabled }
}
