package it.kituwa.stackmate.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class PortainerClient(private val http: Http) {

    suspend fun fetch(server: Server, secret: String): ServerSnapshot {
        val now = System.currentTimeMillis()
        val base = server.baseUrl
        val token = resolveToken(base, server, secret)

        val endpoints = http.getJson("$base/api/endpoints", server, secret, token)
            .asArray()
            .filterIsInstance<JsonObject>()

        if (endpoints.isEmpty()) {
            throw ReachabilityException(Reachability.REACHABLE, "No Portainer environments configured")
        }

        val containers = mutableListOf<Container>()
        val stopped = mutableListOf<String>()

        endpoints.forEach { endpoint ->
            val id = endpoint.longOrNullCompat("Id") ?: return@forEach
            val name = endpoint.string("Name") ?: "environment"
            val online = endpoint.intOrNullCompat("Status") ?: 1

            if (online == 0) {
                stopped += name
                return@forEach
            }

            val list = runCatching {
                http.getJson(
                    "$base/api/endpoints/$id/docker/containers/json?all=true",
                    server,
                    secret,
                    token,
                )
            }.getOrNull()?.asArray() ?: return@forEach

            list.filterIsInstance<JsonObject>().forEach { container ->
                containers += Container(
                    name = container.containerName(),
                    image = container.string("Image") ?: "unknown",
                    state = container.containerState(),
                    status = container.string("Status") ?: "",
                    health = container.healthStatus(),
                    endpoint = name,
                )
            }
        }

        val issues = mutableListOf<Issue>()
        stopped.forEach { name ->
            issues += Issue(Severity.CRITICAL, "Environment offline", detail = name)
        }
        containers.filter { !it.isHealthy }.forEach { container ->
            issues += Issue(
                severity = if (container.state.equals("exited", true)) Severity.WARNING else Severity.CRITICAL,
                title = container.name,
                detail = "Container ${container.state} on ${container.endpoint}" +
                    (container.health?.let { ", health $it" } ?: ""),
            )
        }

        val running = containers.count { it.state.equals("running", true) }
        val details = listOf(
            "Environments" to "${endpoints.size - stopped.size}/${endpoints.size} online",
            "Containers" to "$running/${containers.size} running",
        )

        return ServerSnapshot(
            serverId = server.id,
            reachability = Reachability.REACHABLE,
            issues = issues.sortedBy { it.severity.ordinal },
            containers = containers.sortedBy { it.name.lowercase() },
            details = details,
            observedAt = now,
        )
    }

    private suspend fun resolveToken(base: String, server: Server, secret: String): String {
        if (server.authKind == AuthKind.TOKEN) return secret
        if (server.authKind == AuthKind.NONE) return ""

        val payload = buildJsonObject {
            put("username", JsonPrimitive(server.username.orEmpty()))
            put("password", JsonPrimitive(secret))
        }
        return http.postJson("$base/api/auth", payload, server, secret)
            .jsonObject
            .string("jwt")
            ?: throw ReachabilityException(Reachability.AUTH_FAILED, "Portainer did not return a token")
    }

    private fun JsonElement.asArray(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())

    private fun JsonObject.containerName(): String {
        val names = this["Names"] as? JsonArray ?: return string("Id")?.take(12) ?: "container"
        val first = names.firstOrNull() as? JsonPrimitive ?: return "container"
        return first.content.trimStart('/')
    }

    /**
     * Docker's list endpoint reports "State" as a string, but some Portainer
     * versions pass the full inspect object through, where "State" is itself an
     * object. Accept both, and fall back to the "(healthy)" suffix Docker puts in
     * the human-readable Status field.
     */
    private fun JsonObject.containerState(): String {
        val state = this["State"] ?: return "unknown"
        return when (state) {
            is JsonPrimitive -> state.content
            is JsonObject -> state.string("Status") ?: "unknown"
            else -> "unknown"
        }
    }

    private fun JsonObject.healthStatus(): String? {
        val state = this["State"] as? JsonObject
        val nested = state?.string("Health")?.uppercase()
        if (nested != null) return nested
        return when {
            statusContains("healthy") -> "HEALTHY"
            statusContains("unhealthy") -> "UNHEALTHY"
            else -> null
        }
    }

    private fun JsonObject.statusContains(needle: String): Boolean =
        string("Status")?.contains("($needle)", ignoreCase = true) == true

}
