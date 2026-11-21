package com.zeno.spike

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Control panel for the device spikes. Every probe is started by the owner; nothing runs by default. */
class SpikeActivity : ComponentActivity() {

    private var refresh by mutableStateOf(0)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        SpikeLog.write(this, "RUNTIME_PERMISSIONS result=$result")
        refresh++
    }

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        SpikeLog.write(this, "ROLE_REQUEST resultCode=${r.resultCode} held=${roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)}")
        refresh++
    }

    private val roleManager by lazy { getSystemService(RoleManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpikeLog.write(this, "ACTIVITY opened")
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Panel() } }
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }

    @Composable
    private fun Panel() {
        val r = refresh // recompose on refresh
        val prefs = SpikeLog.prefs(this)
        var probeOnBoot by remember { mutableStateOf(prefs.getBoolean(BootProbeReceiver.PREF_PROBE_ON_BOOT, false)) }
        var probeOnVis by remember { mutableStateOf(prefs.getBoolean(SpikeVoiceInteractionService.PREF_PROBE_ON_VIS_READY, false)) }

        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Zeno feasibility spikes", style = MaterialTheme.typography.headlineSmall)
            Text("SPIKE-01 assistant role + hotword · SPIKE-02 boot / first unlock. No audio is ever saved.", style = MaterialTheme.typography.bodySmall)
            Text(
                "assistantRoleAvailable=${roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)}  " +
                    "assistantRoleHeld=${roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)}\n${SpikeLog.deviceState(this@SpikeActivity)}  (refresh $r)",
                fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            )

            Button(onClick = {
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
            }, modifier = Modifier.fillMaxWidth()) { Text("1. Grant microphone + notifications") }

            Button(onClick = {
                runCatching { roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)) }
                    .onFailure { SpikeLog.write(this@SpikeActivity, "ROLE_REQUEST threw ${it.javaClass.simpleName}: ${it.message}") }
            }, modifier = Modifier.fillMaxWidth()) { Text("2a. Request assistant role (API)") }

            Button(onClick = {
                SpikeLog.write(this@SpikeActivity, "OPEN voice input / digital assistant settings")
                startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            }, modifier = Modifier.fillMaxWidth()) { Text("2b. Open Digital assistant settings") }

            Button(onClick = { MicProbeService.start(this@SpikeActivity, "ACTIVITY_60S", 60) }, modifier = Modifier.fillMaxWidth()) {
                Text("3. Mic probe 60 s (turn the screen off during it)")
            }
            Button(onClick = { MicProbeService.start(this@SpikeActivity, "ACTIVITY_5MIN", 300) }, modifier = Modifier.fillMaxWidth()) {
                Text("3b. Mic probe 5 min (screen off + locked)")
            }

            ToggleRow("Probe mic at boot / unlock (SPIKE-02)", probeOnBoot) {
                probeOnBoot = it
                prefs.edit().putBoolean(BootProbeReceiver.PREF_PROBE_ON_BOOT, it).apply()
                SpikeLog.write(this@SpikeActivity, "PREF probe_on_boot=$it")
            }
            ToggleRow("Probe mic when assistant service starts", probeOnVis) {
                probeOnVis = it
                prefs.edit().putBoolean(SpikeVoiceInteractionService.PREF_PROBE_ON_VIS_READY, it).apply()
                SpikeLog.write(this@SpikeActivity, "PREF probe_on_vis_ready=$it")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    SpikeVoiceInteractionService.logPermissions(this@SpikeActivity, "ACTIVITY")
                    refresh++
                }) { Text("Log permissions") }
                OutlinedButton(onClick = { refresh++ }) { Text("Refresh") }
                OutlinedButton(onClick = { SpikeLog.clear(this@SpikeActivity); refresh++ }) { Text("Clear log") }
            }

            Text("Log (newest last)", style = MaterialTheme.typography.titleSmall)
            Text(SpikeLog.read(this@SpikeActivity).takeLast(12_000), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }

    @Composable
    private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
