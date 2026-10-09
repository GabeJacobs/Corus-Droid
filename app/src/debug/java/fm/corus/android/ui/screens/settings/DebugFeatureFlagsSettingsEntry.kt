package fm.corus.android.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fm.corus.android.service.RemoteConfigService
import kotlinx.coroutines.launch

/** This entire page is compiled only into the Debug source set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugFeatureFlagsSettingsEntry(config: RemoteConfigService) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("Debug · Feature Flags") }
    if (!open) return
    Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DebugFeatureFlagsPage(config, onBack = { open = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugFeatureFlagsPage(config: RemoteConfigService, onBack: () -> Unit) {
    val revision by config.revision.collectAsState()
    var search by remember { mutableStateOf("") }
    var overridesOnly by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(config) {
        refreshing = true
        try { config.refreshDebugFeatureFlags() } finally { refreshing = false }
    }
    val flags = remember(revision, search, overridesOnly) {
        config.debugFeatureFlags.filter {
            (!overridesOnly || (it.allowsLocalOverride && config.debugOverride(it.key) != null)) &&
                (it.key.contains(search, ignoreCase = true) || it.title.contains(search, ignoreCase = true))
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Feature Flags") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            }, actions = {
                IconButton(enabled = !refreshing, onClick = {
                    scope.launch {
                        refreshing = true
                        try { config.refreshDebugFeatureFlags() } finally { refreshing = false }
                    }
                }) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(24.dp))
                    else Icon(Icons.Default.Refresh, "Refresh Remote Config")
                }
            })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                DebugForYouPreviewEndedSection()
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
            }
            item {
                Text("Changes apply on this installation and survive relaunch. Server permissions and rollout checks still apply. Reopen a screen if it loads flags only on entry.", style = MaterialTheme.typography.bodySmall)
                Text("New Remote Config keys appear after refresh. Your Mix supports a local visibility override. Other backend flags and keys this build cannot override are read-only.", style = MaterialTheme.typography.bodySmall)
                config.debugServerCatalogMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Find a flag") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show overrides only", Modifier.weight(1f))
                    Switch(checked = overridesOnly, onCheckedChange = { overridesOnly = it })
                }
                TextButton(enabled = config.debugOverrideCount > 0, onClick = { config.resetDebugOverrides() }) {
                    Text("Reset All Overrides (${config.debugOverrideCount})")
                }
                Text("Reset restores this build’s normal values, including its Debug presets. Remote Config is never edited.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                Text("${flags.size} Feature Flags", style = MaterialTheme.typography.titleSmall)
            }
            items(flags, key = { it.rowId }) { flag ->
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(flag.title, style = MaterialTheme.typography.bodyLarge)
                            Text(flag.key, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                        if (flag.allowsLocalOverride) {
                            Switch(checked = config.debugOverride(flag.key) ?: flag.effectiveValue(), onCheckedChange = { config.setDebugOverride(flag.key, it) })
                        }
                    }
                    if (flag.allowsLocalOverride) {
                        if (flag.namespace == "server") {
                            val backend = when (flag.rawValue) { "true" -> "ON"; "false" -> "OFF"; else -> "Unavailable" }
                            Text("Backend: $backend · Local: ${if (flag.effectiveValue()) "ON" else "OFF"}", style = MaterialTheme.typography.bodySmall)
                            Text("Changes Your Mix visibility immediately on this installation. Loading the feed still requires backend tester access. Reset restores server-controlled visibility.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("${config.debugSource(flag.key)}: ${if (config.debugRemoteValue(flag.key)) "ON" else "OFF"} · Effective: ${if (flag.effectiveValue()) "ON" else "OFF"}", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        val value = when (flag.rawValue?.lowercase()) {
                            "true" -> "ON"
                            "false" -> "OFF"
                            "" -> "(empty)"
                            null -> "Unavailable — refresh to check"
                            else -> flag.rawValue.orEmpty()
                        }
                        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
                        Text(if (flag.namespace == "server") "Backend · Read only" else "${config.debugSource(flag.key)} · Read only in this build", style = MaterialTheme.typography.bodySmall)
                    }
                    if (flag.allowsLocalOverride && config.debugOverride(flag.key) != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Local override", Modifier.weight(1f), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = { config.setDebugOverride(flag.key, null) }) { Text("Reset") }
                        }
                    }
                }
                HorizontalDivider()
            }
            if (flags.isEmpty()) item { Text("No matching flags", Modifier.padding(vertical = 24.dp)) }
        }
    }
}
