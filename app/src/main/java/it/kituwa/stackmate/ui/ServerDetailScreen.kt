package it.kituwa.stackmate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.kituwa.stackmate.data.Reachability
import it.kituwa.stackmate.data.Server
import it.kituwa.stackmate.data.ServerSnapshot
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDetailScreen(
    serverId: String,
    viewModel: StackMateViewModel,
    onBack: () -> Unit,
    onRemoved: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val server = state.servers.firstOrNull { it.id == serverId }
    val snapshot = state.snapshots[serverId]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(server?.name ?: "Server") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(
                        onClick = {
                            server?.let { viewModel.delete(it) }
                            onRemoved()
                        },
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Remove server")
                    }
                },
            )
        },
    ) { padding ->
        if (server == null) {
            Text("This server is no longer configured.", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HeaderCard(server, snapshot) }

            if (snapshot != null && snapshot.issues.isNotEmpty()) {
                item { SectionTitle("Active issues") }
                items(snapshot.issues) { issue ->
                    IssueCard(
                        title = issue.title,
                        detail = issue.detail,
                        severity = issue.severity.name,
                        since = issue.since,
                    )
                }
            }

            if (snapshot != null && snapshot.containers.isNotEmpty()) {
                item { SectionTitle("Containers") }
                items(snapshot.containers) { container ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            TitleBlock(container.name, container.endpoint)
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            MetaRow("State", container.state)
                            MetaRow("Image", container.image)
                            container.health?.let { MetaRow("Health", it) }
                            if (container.status.isNotBlank()) MetaRow("Status", container.status)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleBlock(primary: String, secondary: String) {
    Column {
        Text(primary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            secondary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun HeaderCard(server: Server, snapshot: ServerSnapshot?) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusHeader(server, snapshot)
            MetaRow("Type", server.type.displayName)
            MetaRow("Address", server.baseUrl)
            snapshot?.details?.forEach { (label, value) -> MetaRow(label, value) }

            if (snapshot != null) {
                val lastSuccess = snapshot.lastSuccessAt
                if (lastSuccess != null) {
                    Text(
                        "Last successful check " +
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(lastSuccess)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    Text(
                        "No successful check yet",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            stalenessWarning(snapshot?.reachability ?: Reachability.UNKNOWN)?.let { notice ->
                Text(
                    notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun IssueCard(title: String, detail: String?, severity: String, since: Long?) {
    val color = when (severity) {
        "CRITICAL" -> StatusColors.bad
        "WARNING" -> StatusColors.warn
        else -> StatusColors.unknown
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            TitleBlock(title, severity.lowercase().replaceFirstChar { it.uppercase() })
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
            since?.let {
                Text(
                    "Since " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
