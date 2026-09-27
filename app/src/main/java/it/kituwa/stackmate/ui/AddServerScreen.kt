package it.kituwa.stackmate.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import it.kituwa.stackmate.data.AuthKind
import it.kituwa.stackmate.data.ServerType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerScreen(
    viewModel: StackMateViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val requestNotifications = remember {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                (context as? Activity)?.requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATIONS,
                )
            }
        }
    }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ServerType.GRAFANA) }
    var authKind by remember { mutableStateOf(AuthKind.TOKEN) }
    var username by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add a server") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Server type", style = MaterialTheme.typography.labelLarge)
            ChipRow(
                options = ServerType.entries,
                selected = type,
                label = { it.displayName },
                onSelect = { type = it; testResult = null },
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it; testResult = null },
                label = { Text("Server URL") },
                placeholder = { Text("https://grafana.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Display name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Authentication", style = MaterialTheme.typography.labelLarge)
            ChipRow(
                options = AuthKind.entries,
                selected = authKind,
                label = {
                    when (it) {
                        AuthKind.TOKEN -> "API token"
                        AuthKind.BASIC -> "Username / password"
                        AuthKind.NONE -> "None"
                    }
                },
                onSelect = { authKind = it; testResult = null },
            )

            if (authKind == AuthKind.BASIC) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (authKind != AuthKind.NONE) {
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it; testResult = null },
                    label = {
                        Text(
                            when {
                                type == ServerType.GRAFANA && authKind == AuthKind.TOKEN -> "Grafana service account token"
                                type == ServerType.PORTAINER && authKind == AuthKind.TOKEN -> "Portainer JWT"
                                else -> "Password"
                            },
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Card(Modifier.fillMaxWidth()) {
                Text(
                    when {
                        type == ServerType.GRAFANA && authKind == AuthKind.TOKEN ->
                            "In Grafana: Administration → Users and access → Service accounts. " +
                                "Create one with Viewer role and add the alerts:read permission."
                        type == ServerType.GRAFANA && authKind == AuthKind.BASIC ->
                            "Basic auth only works if your Grafana sits behind a reverse proxy that accepts it."
                        type == ServerType.PORTAINER && authKind == AuthKind.BASIC ->
                            "Your Portainer username and password. StackMate exchanges them for a short-lived JWT."
                        type == ServerType.PORTAINER && authKind == AuthKind.TOKEN ->
                            "Paste a Portainer JWT. It expires, so username and password is usually easier."
                        else -> "No credentials will be sent."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp),
                )
            }

            Text(
                "Credentials are encrypted with the Android Keystore and never leave this device " +
                    "except to reach your own server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    testing = true
                    testResult = null
                    viewModel.testConnection(url, type, authKind, username, secret) { message ->
                        testResult = message
                        testing = false
                    }
                },
                enabled = url.isNotBlank() && !testing && !testing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (testing) "Testing…" else "Test connection")
            }

            testResult?.let {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                }
            }

            Button(
                onClick = {
                    viewModel.save(null, name, type, url, authKind, username, secret) {
                        if (it) {
                            requestNotifications()
                            onDone()
                        }
                    }
                },
                enabled = url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save server")
            }

            Text(
                "StackMate checks your servers in the background every 30 minutes and will " +
                    "notify you when something needs attention.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val REQUEST_NOTIFICATIONS = 1001

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}
