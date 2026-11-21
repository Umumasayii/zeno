package com.zeno.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

/*
 * Commands and Automations screens plus shared building blocks. Every value shown is real (domain defaults or the intent catalogue), and every
 * feature that is not built yet says so. No decorative control is presented as interactive
 * (docs/07 "States per main screen").
 */

@Composable
internal fun ScreenTitle(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun InfoCard(title: String, body: String, modifier: Modifier = Modifier) {
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
internal fun NotYetBadge(text: String = "Not built yet") {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = StatusWarning,
        modifier = Modifier
            .background(StatusWarning.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
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
