package it.kituwa.stackmate.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ServerType {
    @SerialName("grafana")
    GRAFANA,

    @SerialName("portainer")
    PORTAINER;

    val displayName: String
        get() = when (this) {
            GRAFANA -> "Grafana"
            PORTAINER -> "Portainer"
        }
}

@Serializable
enum class AuthKind {
    @SerialName("none")
    NONE,

    @SerialName("token")
    TOKEN,

    @SerialName("basic")
    BASIC,
}

@Serializable
data class Server(
    val id: String,
    val name: String,
    val type: ServerType,
    val baseUrl: String,
    val authKind: AuthKind,
    val username: String? = null,
    val secret: String = "",
    val allowInsecure: Boolean = true,
    val enabled: Boolean = true,
)

@Serializable
enum class Reachability { UNKNOWN, REACHABLE, UNREACHABLE, AUTH_FAILED }

@Serializable
enum class Severity { CRITICAL, WARNING, INFO, RECOVERY }

@Serializable
data class Issue(
    val severity: Severity,
    val title: String,
    val detail: String? = null,
    val since: Long? = null,
)

@Serializable
data class Container(
    val name: String,
    val image: String,
    val state: String,
    val status: String,
    val health: String? = null,
    val endpoint: String,
) {
    val isHealthy: Boolean
        get() = when (state.lowercase()) {
            "running" -> health == null || health.equals("healthy", ignoreCase = true)
            else -> false
        }
}

@Serializable
data class ServerSnapshot(
    val serverId: String,
    val reachability: Reachability,
    val issues: List<Issue> = emptyList(),
    val containers: List<Container> = emptyList(),
    val details: List<Pair<String, String>> = emptyList(),
    val observedAt: Long,
    val message: String? = null,
)
