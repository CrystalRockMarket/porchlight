package it.kituwa.stackmate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.kituwa.stackmate.data.Server
import it.kituwa.stackmate.data.ServerSnapshot

object StatusColors {
    val ok = Color(0xFF2E9E63)
    val warn = Color(0xFFD08A1E)
    val bad = Color(0xFFC0392B)
    val unknown = Color(0xFF7A8B9C)
    val stale = Color(0xFF6B5BA5)
}

enum class ServerStatus { OK, WARNING, CRITICAL, UNREACHABLE, UNKNOWN, STALE }

fun statusOf(snapshot: ServerSnapshot?): ServerStatus {
    if (snapshot == null) return ServerStatus.UNKNOWN
    if (snapshot.reachability == it.kituwa.stackmate.data.Reachability.UNREACHABLE) {
        return ServerStatus.UNREACHABLE
    }
    if (snapshot.reachability == it.kituwa.stackmate.data.Reachability.AUTH_FAILED) {
        return ServerStatus.CRITICAL
    }
    val worst = snapshot.issues.minByOrNull { it.severity.ordinal }?.severity
    return when (worst) {
        it.kituwa.stackmate.data.Severity.CRITICAL -> ServerStatus.CRITICAL
        it.kituwa.stackmate.data.Severity.WARNING -> ServerStatus.WARNING
        it.kituwa.stackmate.data.Severity.INFO -> ServerStatus.WARNING
        else -> ServerStatus.OK
    }
}

fun colorFor(status: ServerStatus) = when (status) {
    ServerStatus.OK -> StatusColors.ok
    ServerStatus.WARNING -> StatusColors.warn
    ServerStatus.CRITICAL, ServerStatus.UNREACHABLE -> StatusColors.bad
    ServerStatus.UNKNOWN, ServerStatus.STALE -> StatusColors.unknown
}

fun labelFor(status: ServerStatus) = when (status) {
    ServerStatus.OK -> "All clear"
    ServerStatus.WARNING -> "Needs attention"
    ServerStatus.CRITICAL -> "Critical"
    ServerStatus.UNREACHABLE -> "Cannot reach"
    ServerStatus.UNKNOWN -> "No data yet"
    ServerStatus.STALE -> "Stale data"
}

@Composable
fun StatusDot(status: ServerStatus, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(12.dp)
            .background(colorFor(status), CircleShape),
    )
}

@Composable
fun StatusHeader(server: Server, snapshot: ServerSnapshot?) {
    val status = statusOf(snapshot)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusDot(status)
        Column(Modifier.weight(1f)) {
            Text(server.name, style = MaterialTheme.typography.titleMedium)
            Text(
                labelFor(status),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MetaRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
