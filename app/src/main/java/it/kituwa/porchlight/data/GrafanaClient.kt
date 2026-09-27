package it.kituwa.porchlight.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GrafanaClient(private val http: Http) {

    suspend fun fetch(server: Server, secret: String): ServerSnapshot {
        val now = System.currentTimeMillis()
        val base = server.baseUrl

        verifyCredentials(base, server, secret)

        val version = runCatching { http.getJson("$base/api/health", server, secret) }
            .getOrElse { throw translate(it) }
            .jsonObject
            .string("version") ?: "unknown"

        val issues = mutableListOf<Issue>()

        val alerts = runCatching {
            http.getJson("$base/api/alertmanager/grafana/api/v2/alerts", server, secret)
        }
        val alertResult = alerts.getOrNull()

        if (alertResult is JsonArray) {
            alertResult.jsonArray.filterIsInstance<JsonObject>().forEach { alert ->
                val state = alert.string("status")?.uppercase() ?: return@forEach
                if (state == "OK" || state == "RESOLVED") return@forEach
                val labels = alert["labels"] as? JsonObject
                val annotations = alert["annotations"] as? JsonObject
                val name = labels?.string("alertname") ?: "Alert"
                val severity = when (labels?.string("severity")?.lowercase()) {
                    "critical" -> Severity.CRITICAL
                    "warning", "warn" -> Severity.WARNING
                    else -> Severity.INFO
                }
                issues += Issue(
                    severity = severity,
                    title = name,
                    detail = annotations?.string("summary")
                        ?: annotations?.string("description")
                        ?: alert.string("message"),
                    since = alert["startsAt"]?.let { start ->
                        (start as? JsonPrimitive)?.content?.let { parseTimestamp(it) }
                    },
                )
            }
        }

        val details = mutableListOf(
            "Grafana" to version,
            "Firing" to (alertResult?.let { issues.size.toString() } ?: "unavailable"),
        )

        if (alertResult == null) {
            details += "Alert API" to "no permission (Viewer role cannot read alerts)"
        }

        return ServerSnapshot(
            serverId = server.id,
            reachability = Reachability.REACHABLE,
            issues = issues.sortedBy { it.severity.ordinal },
            details = details,
            observedAt = now,
        )
    }

    /**
     * Grafana's /api/health is unauthenticated, so a bad token still returns 200 and
     * the server would otherwise look connected. /api/user requires auth, which makes
     * it the only reliable way to prove the credentials before trusting anything else.
     */
    private suspend fun verifyCredentials(base: String, server: Server, secret: String) {
        if (server.authKind == it.kituwa.porchlight.data.AuthKind.NONE) return
        try {
            http.getJson("$base/api/user", server, secret)
        } catch (error: HttpException) {
            if (error.status == 401 || error.status == 403) {
                throw ReachabilityException(Reachability.AUTH_FAILED, "Authentication rejected")
            }
        }
    }

    private fun translate(error: Throwable): Throwable = when (error) {
        is HttpException -> when (error.status) {
            401, 403 -> ReachabilityException(Reachability.AUTH_FAILED, "Authentication rejected (${error.status})")
            else -> error
        }
        else -> error
    }

    companion object {
        fun parseTimestamp(raw: String): Long? = runCatching {
            val pattern =
                if (raw.contains('.')) "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" else "yyyy-MM-dd'T'HH:mm:ssXXX"
            java.text.SimpleDateFormat(pattern, java.util.Locale.US).parse(raw)?.time
        }.getOrNull()
    }
}

class ReachabilityException(
    val reachability: Reachability,
    override val message: String,
) : Exception(message)
