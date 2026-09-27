package it.kituwa.stackmate.ui

import it.kituwa.stackmate.data.Reachability
import it.kituwa.stackmate.data.Server
import it.kituwa.stackmate.data.ServerSnapshot

data class Summary(
    val headline: String,
    val detail: String,
    val healthy: Boolean,
    val alarming: Boolean,
)

/**
 * The one place that decides whether to tell the user everything is fine.
 *
 * A server we could not reach is NOT healthy, even though it reports no issues:
 * we simply do not know its state. Counting only issues here made the banner read
 * "Everything looks healthy" while a server was down, which is the single most
 * dangerous thing this app could do.
 */
fun summarize(
    servers: List<Server>,
    snapshots: Map<String, ServerSnapshot>,
): Summary {
    if (servers.isEmpty()) {
        return Summary(
            headline = "No servers yet",
            detail = "Add a server to get started",
            healthy = false,
            alarming = false,
        )
    }

    val unverified = servers.count { snapshots[it.id] == null }
    val unreachable = servers.count {
        val snapshot = snapshots[it.id]
        snapshot != null && snapshot.reachability != Reachability.REACHABLE
    }
    val problems = servers.sumOf { snapshots[it.id]?.issues?.size ?: 0 }
    val attention = problems + unreachable

    val serverWord = if (servers.size == 1) "server" else "servers"
    val healthyCount = servers.size - unreachable

    if (attention > 0) {
        val parts = mutableListOf<String>()
        if (unreachable > 0) {
            parts += "$unreachable of ${servers.size} $serverWord ${if (unreachable == 1) "is" else "are"} not reporting"
        }
        if (problems > 0) parts += "$problems active ${if (problems == 1) "problem" else "problems"}"
        return Summary(
            headline = "Needs attention",
            detail = parts.joinToString(" · "),
            healthy = false,
            alarming = true,
        )
    }

    if (unverified > 0) {
        return Summary(
            headline = "Checking your $serverWord",
            detail = "$unverified of ${servers.size} not yet verified",
            healthy = false,
            alarming = false,
        )
    }

    return Summary(
        headline = "Everything looks healthy",
        detail = "$healthyCount of ${servers.size} $serverWord reporting",
        healthy = true,
        alarming = false,
    )
}
