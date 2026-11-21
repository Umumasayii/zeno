package com.zeno.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zeno.assistant.ui.theme.StatusWarning
import com.zeno.domain.intent.IntentGroup
import com.zeno.domain.intent.IntentKind
import com.zeno.domain.settings.SmsPolicy
import com.zeno.domain.settings.ZenoSettings

/*
 * M0 screens. Every value shown is real (domain defaults or the intent catalogue), and every
 * feature that is not built yet says so. No decorative control is presented as interactive
 * (docs/07 "States per main screen").
 */

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoCard(title: String, body: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NotYetBadge(text: String = "Not built yet") {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = StatusWarning,
        modifier = Modifier
            .background(StatusWarning.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

// ---------------------------------------------------------------- Home (03 / 31)

@Composable
fun HomeScreen(settings: ZenoSettings) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenTitle(settings.assistantName, "Offline voice assistant · development build M0") }
        item {
            // Honest listening state (docs/07 "Home listening toggle"; screen 31 "Listening paused").
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(112.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.MicOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Text("Listening is off", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Zeno is not using the microphone. Wake-word listening has not been built yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                )
            }
        }
        item { InfoCard("Upcoming automation", "None. You haven't created any automations.") }
        item { InfoCard("Recent activity", "No actions have been run.") }
        item {
            InfoCard(
                "What works in this build",
                "Navigation and the safety rules (tested in code) only. Voice, phone actions, SMS, automations and learning come in later milestones.",
            )
        }
    }
}

// ---------------------------------------------------------------- Commands (04)

internal fun IntentKind.displayName(): String =
    name.lowercase().split('_').joinToString(" ").replaceFirstChar { it.uppercase() }

internal fun IntentGroup.displayName(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
fun CommandsScreen() {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = IntentKind.entries.filter { k ->
        query.isBlank() || k.displayName().contains(query, ignoreCase = true) || k.group.displayName().contains(query, ignoreCase = true)
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenTitle("Voice commands", "Planned command catalogue with each command's risk level") }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Search commands") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (filtered.isEmpty()) {
            item { InfoCard("No matches", "No planned command matches \"$query\".") }
        }
        filtered.groupBy { it.group }.forEach { (group, kinds) ->
            item {
                Text(
                    group.displayName(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
                )
            }
            items(kinds, key = { it.name }) { kind ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(kind.displayName(), style = MaterialTheme.typography.bodyLarge)
                        Text("Risk ${kind.risk.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    NotYetBadge()
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Automations (05 / 30)

@Composable
fun AutomationsScreen() {
    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenTitle("Automations", "Your routines: scheduled, event-based and approved learned ones") }
        item {
            InfoCard(
                "No automations yet",
                "The automation builder comes in milestone M5. There is no limit on how many routines you can create, and none are installed by default.",
            )
        }
        item {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Create automation (available in M5)")
            }
        }
    }
}

// ---------------------------------------------------------------- Settings (07)

private fun SmsPolicy.label() = when (this) {
    SmsPolicy.IMMEDIATE -> "Send immediately"
    SmsPolicy.ALWAYS_CONFIRM -> "Always confirm"
    SmsPolicy.SMART -> "Smart confirmation"
}

private fun onOff(v: Boolean) = if (v) "On" else "Off"

@Composable
fun SettingsScreen(settings: ZenoSettings) {
    val rows = listOf(
        "Background listening" to onOff(settings.listeningEnabled),
        "Wake phrase" to settings.wakePhrase,
        "SMS sending" to settings.smsPolicy.label(),
        "Read private content while locked" to onOff(settings.allowPrivateReadoutWhileLocked),
        "Adaptive learning" to onOff(settings.learningEnabled),
        "Root controls" to if (settings.enabledRootOperations.isEmpty()) "Off" else "${settings.enabledRootOperations.size} enabled",
        "Log retention" to "${settings.retentionDays} days",
    )
    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenTitle("Settings", "Safe defaults. You'll be able to edit them in milestone M1.") }
        items(rows, key = { it.first }) { (label, value) ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
