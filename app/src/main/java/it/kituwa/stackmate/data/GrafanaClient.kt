package it.kituwa.stackmate.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GrafanaClient(private val http: Http) {

    suspend fun fetch(server: Server, secret: String): ServerSnapshot {
        val now = System.currentTimeMillis()
        val base = server.baseUrl

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
                if (state == "OK") return@forEach
                val labels = alert["labels"] as? JsonObject
                val name = labels?.string("alertname") ?: "Alert"
                val severity = when (labels?.string("severity")?.lowercase()) {
                    "critical" -> Severity.CRITICAL
                    "warning", "warn" -> Severity.WARNING
                    else -> Severity.INFO
                }
                issues += Issue(
                    severity = severity,
                    title = name,
                    detail = labels?.string("summary") ?: alert.string("message"),
                    since = alert["startsAt"]?.jsonPrimitive?.contentOrNull?.let { parseTimestamp(it) },
                )
            }
        }

        val details = mutableListOf(
            "Grafana" to version,
            "Alerts" to (alertResult?.let { count(it) } ?: "unavailable"),
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

    private fun count(element: kotlinx.serialization.json.JsonElement): String =
        (element as? JsonArray)?.size?.toString() ?: "unknown"

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
