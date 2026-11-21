package com.zeno.assistant.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zeno.assistant.platform.CapabilityPlan
import com.zeno.assistant.platform.CapabilityState
import com.zeno.assistant.platform.CapabilityStatus
import com.zeno.assistant.platform.PlatformStatusSource
import com.zeno.assistant.ui.theme.ZenoTheme
import com.zeno.domain.model.ModelInventory
import com.zeno.domain.security.Capability
import com.zeno.domain.settings.SmsPolicy
import com.zeno.domain.settings.ZenoSettings
import com.zeno.domain.status.PlatformSnapshot
import com.zeno.testing.InMemorySettingsRepository
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** UI tests against the real ViewModel with in-memory settings and a fake platform (no Android grants). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w393dp-h851dp-xxhdpi") // Pixel 4a 5G screen size
class NavigationTest {

    @get:Rule val rule = createComposeRule()

    private class FakePlatform(var snap: PlatformSnapshot) : PlatformStatusSource {
        override fun snapshot(wakeWordEngineReady: Boolean, listeningServiceRunning: Boolean) =
            snap.copy(wakeWordEngineReady = wakeWordEngineReady, listeningServiceRunning = listeningServiceRunning)
        override fun capabilities() = Capability.entries.map {
            CapabilityStatus(it, if (it == Capability.ROOT) CapabilityState.NOT_CHECKED else CapabilityState.NOT_GRANTED, CapabilityPlan.usedFrom.getValue(it), false)
        }
    }

    // What the Pixel reports for the M1 build: unlocked, no mic grant, Zeno not the assistant.
    private val m1Phone = PlatformSnapshot(
        userUnlocked = true, microphoneGranted = false, assistantRoleHeld = false,
        wakeWordEngineReady = false, listeningServiceRunning = false,
    )

    private lateinit var repo: InMemorySettingsRepository

    private fun launch(initial: ZenoSettings = ZenoSettings(), snap: PlatformSnapshot = m1Phone) {
        repo = InMemorySettingsRepository(initial)
        val vm = ZenoViewModel(repo, FakePlatform(snap), { ModelInventory.EMPTY }, "/storage/emulated/0/Android/data/com.zeno.assistant/files/models", false, Dispatchers.Unconfined)
        rule.setContent { ZenoTheme { ZenoRoot(vm) } }
    }

    private fun openSettings() = rule.onNodeWithTag("tab_settings").performClick()
    private fun scrollSettingsTo(tag: String) = rule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag(tag))

    @Test fun `home shows honest listening paused state`() {
        launch()
        rule.onNodeWithText("Listening paused").assertIsDisplayed()
        rule.onNodeWithTag("home_listening_switch").assertIsOff()
    }

    @Test fun `turning listening on persists and shows the real blocker, never active`() {
        launch()
        rule.onNodeWithTag("home_listening_switch").performClick()
        rule.waitForIdle()
        assertTrue(repo.settings.value.listeningEnabled)
        rule.onNodeWithText("Microphone permission needed").assertIsDisplayed()
        rule.onNodeWithText("Listening for your wake phrase").assertDoesNotExist()

        rule.onNodeWithTag("home_listening_switch").performClick()
        rule.waitForIdle()
        assertFalse(repo.settings.value.listeningEnabled)
        rule.onNodeWithText("Listening paused").assertIsDisplayed()
    }

    @Test fun `waiting for first unlock is shown before anything else`() {
        launch(ZenoSettings(listeningEnabled = true), m1Phone.copy(userUnlocked = false))
        rule.onNodeWithText("Waiting for first unlock").assertIsDisplayed()
    }

    @Test fun `all four tabs navigate`() {
        launch()
        rule.onNodeWithTag("tab_commands").performClick()
        rule.onNodeWithText("Voice commands").assertIsDisplayed()
        rule.onNodeWithTag("tab_automations").performClick()
        rule.onNodeWithText("No automations yet").assertIsDisplayed()
        openSettings()
        rule.onNodeWithText("Kept on this phone only. Nothing is uploaded.").assertIsDisplayed()
        rule.onNodeWithTag("tab_home").performClick()
        rule.onNodeWithText("Listening paused").assertIsDisplayed()
    }

    @Test fun `unbuilt create-automation button is visibly disabled`() {
        launch()
        rule.onNodeWithTag("tab_automations").performClick()
        rule.onNodeWithText("Create automation (available in M5)").assertIsNotEnabled()
    }

    @Test fun `command search filters the catalogue`() {
        launch()
        rule.onNodeWithTag("tab_commands").performClick()
        rule.onNodeWithText("Search commands").performTextInput("screenshot")
        rule.onNodeWithText("Take screenshot").assertIsDisplayed()
        rule.onNodeWithText("Send sms").assertDoesNotExist()
    }

    @Test fun `sms policy defaults to always confirm and can be changed`() {
        launch()
        openSettings()
        scrollSettingsTo("sms_ALWAYS_CONFIRM")
        rule.onNodeWithTag("sms_ALWAYS_CONFIRM").assertIsSelected()
        rule.onNodeWithTag("sms_SMART").performClick()
        rule.waitForIdle()
        assertEquals(SmsPolicy.SMART, repo.settings.value.smsPolicy)
        rule.onNodeWithTag("sms_SMART").assertIsSelected()
    }

    @Test fun `switches write to the settings store`() {
        launch()
        openSettings()
        scrollSettingsTo("private_readout_switch")
        rule.onNodeWithTag("private_readout_switch").assertIsOff().performClick()
        scrollSettingsTo("automations_paused_switch")
        rule.onNodeWithTag("automations_paused_switch").performClick()
        scrollSettingsTo("retention_7")
        rule.onNodeWithTag("retention_7").performClick()
        rule.waitForIdle()
        val s = repo.settings.value
        assertTrue(s.allowPrivateReadoutWhileLocked)
        assertTrue(s.automationsPaused)
        assertEquals(7, s.retentionDays)
    }

    @Test fun `learning and root cannot be enabled in this build`() {
        launch()
        openSettings()
        scrollSettingsTo("learning_switch")
        rule.onNodeWithTag("learning_switch").assertIsNotEnabled().assertIsOff().performClick()
        scrollSettingsTo("root_switch")
        rule.onNodeWithTag("root_switch").assertIsNotEnabled().performClick()
        rule.waitForIdle()
        assertFalse(repo.settings.value.learningEnabled)
        assertTrue(repo.settings.value.enabledRootOperations.isEmpty())
    }

    @Test fun `assistant name is saved and blank names are refused`() {
        launch()
        openSettings()
        rule.onNodeWithTag("assistant_name_field").performTextClearance()
        rule.onNodeWithTag("assistant_name_field").performTextInput("Ada")
        rule.onNodeWithTag("assistant_name_save").performClick()
        rule.waitForIdle()
        assertEquals("Ada", repo.settings.value.assistantName)

        rule.onNodeWithTag("assistant_name_field").performTextClearance()
        rule.onNodeWithTag("assistant_name_field").performTextInput("   ")
        rule.onNodeWithTag("assistant_name_save").performClick()
        rule.waitForIdle()
        assertEquals("Ada", repo.settings.value.assistantName)
        rule.onNodeWithText("The assistant name can't be empty.").assertIsDisplayed()
    }

    @Test fun `wake phrase without a compatible model is saved as text with a warning`() {
        launch()
        openSettings()
        scrollSettingsTo("wake_phrase_field")
        rule.onNodeWithText("Saved as text only", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("wake_phrase_field").performTextClearance()
        rule.onNodeWithTag("wake_phrase_field").performTextInput("Zeno")
        rule.onNodeWithTag("wake_phrase_save").performClick()
        rule.waitForIdle()
        assertEquals("Hey Zeno", repo.settings.value.wakePhrase)
        rule.onNodeWithText("Use at least two words", substring = true).assertIsDisplayed()
    }

    @Test fun `reset asks first, then restores safe defaults`() {
        launch(ZenoSettings(listeningEnabled = true, assistantName = "Ada", smsPolicy = SmsPolicy.IMMEDIATE))
        openSettings()
        scrollSettingsTo("reset_button")
        rule.onNodeWithTag("reset_button").performClick()
        rule.onNodeWithText("Restore safe defaults?").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()
        assertEquals("Ada", repo.settings.value.assistantName)

        rule.onNodeWithTag("reset_button").performClick()
        rule.onNodeWithTag("reset_confirm").performClick()
        rule.waitForIdle()
        assertEquals(ZenoSettings(), repo.settings.value)
    }

    @Test fun `status screen shows grant states, assistant shortcut and missing models`() {
        launch(ZenoSettings(listeningEnabled = true))
        rule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_open_status"))
        rule.onNodeWithTag("home_open_status").performClick()
        rule.onNodeWithText("Permissions & status").assertIsDisplayed()
        rule.onNodeWithTag("status_listening").assertIsDisplayed()
        rule.onNodeWithTag("status_role").assertIsDisplayed()
        rule.onNodeWithTag("open_assistant_settings").assertIsDisplayed()
        rule.onNodeWithTag("status_list").performScrollToNode(hasTestTag("cap_MICROPHONE"))
        rule.onNodeWithTag("cap_MICROPHONE").assertIsDisplayed()
        rule.onNodeWithTag("status_list").performScrollToNode(hasTestTag("cap_ROOT"))
        rule.onNodeWithText("Off · not checked").assertIsDisplayed()
        rule.onNodeWithTag("status_list").performScrollToNode(hasTestTag("models_empty"))
        rule.onNodeWithText("No models installed").assertIsDisplayed()
        rule.onNodeWithTag("status_list").performScrollToNode(hasTestTag("model_WAKE_WORD"))
        rule.onNodeWithTag("model_WAKE_WORD").assertIsDisplayed()
    }

    @Test fun `settings listening switch reflects stored value`() {
        launch(ZenoSettings(listeningEnabled = true))
        openSettings()
        scrollSettingsTo("listening_switch")
        rule.onNodeWithTag("listening_switch").assertIsOn()
    }
}
