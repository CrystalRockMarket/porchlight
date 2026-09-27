package it.kituwa.stackmate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.kituwa.stackmate.data.Server
import it.kituwa.stackmate.data.ServerSnapshot
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: StackMateViewModel,
    onOpenServer: (Server) -> Unit,
    onAddServer: () -> Unit,
    onAbout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StackMate") },
                actions = {
                    IconButton(onClick = onAbout) {
                        Icon(Icons.Outlined.Info, contentDescription = "About StackMate")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddServer) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SummaryCard(state)
            }

            if (state.servers.isEmpty()) {
                item { EmptyState(onAddServer, onAbout) }
            } else {
                items(state.servers, key = { it.id }) { server ->
                    ServerCard(
                        server = server,
                        snapshot = state.snapshots[server.id],
                        refreshing = state.refreshing,
                        onClick = { onOpenServer(server) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: OverviewState) {
    val problems = state.problemCount
    val unverified = state.servers.count { state.snapshots[it.id] == null }
    val headline = when {
        state.servers.isEmpty() -> "No servers yet"
        problems > 0 -> "$problems active ${if (problems == 1) "problem" else "problems"}"
        unverified == state.servers.size -> "Checking your servers…"
        else -> "Everything looks healthy"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (state.refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(start = 12.dp).size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            Text(
                "${state.servers.size} ${if (state.servers.size == 1) "server" else "servers"} configured" +
                    if (unverified > 0) " · $unverified not yet verified" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServerCard(
    server: Server,
    snapshot: ServerSnapshot?,
    refreshing: Boolean,
    onClick: () -> Unit,
) {
    val status = statusOf(snapshot)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            StatusHeader(server, snapshot)
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            snapshot?.details?.forEach { (label, value) -> MetaRow(label, value) }

            when {
                snapshot == null ->
                    Text(
                        "No status yet. Pull to refresh or open the server to test it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                status == ServerStatus.UNREACHABLE ->
                    Text(
                        snapshot.message ?: "Could not reach this server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusColors.bad,
                    )
                snapshot.issues.isNotEmpty() ->
                    snapshot.issues.take(3).forEach { issue ->
                        Text(
                            "• ${issue.title}" + (issue.detail?.let { " — $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorFor(
                                if (issue.severity == it.kituwa.stackmate.data.Severity.CRITICAL) {
                                    ServerStatus.CRITICAL
                                } else {
                                    ServerStatus.WARNING
                                },
                            ),
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
            }

            Text(
                "Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(snapshot?.observedAt ?: 0L))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(onAddServer: () -> Unit, onAbout: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Connect your own stack", style = MaterialTheme.typography.titleMedium)
            Text(
                "StackMate talks only to the servers you add. Nothing is sent anywhere else, " +
                    "and there is no account to create.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Grafana and Portainer are supported today.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Add a server",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onAddServer).padding(top = 4.dp),
            )
            Text(
                "About StackMate",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onAbout),
            )
        }
    }
}
