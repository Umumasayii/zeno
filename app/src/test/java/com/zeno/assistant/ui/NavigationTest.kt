package com.zeno.assistant.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zeno.assistant.ui.theme.ZenoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule val rule = createComposeRule()

    private fun launch() = rule.setContent { ZenoTheme { ZenoApp() } }

    @Test fun `home shows honest listening-off state`() {
        launch()
        rule.onNodeWithText("Listening is off").assertIsDisplayed()
    }

    @Test fun `all four tabs navigate`() {
        launch()
        rule.onNodeWithTag("tab_commands").performClick()
        rule.onNodeWithText("Voice commands").assertIsDisplayed()
        rule.onNodeWithTag("tab_automations").performClick()
        rule.onNodeWithText("No automations yet").assertIsDisplayed()
        rule.onNodeWithTag("tab_settings").performClick()
        rule.onNodeWithText("Adaptive learning").assertIsDisplayed()
        rule.onNodeWithTag("tab_home").performClick()
        rule.onNodeWithText("Listening is off").assertIsDisplayed()
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

    @Test fun `settings show the safe defaults`() {
        launch()
        rule.onNodeWithTag("tab_settings").performClick()
        rule.onNodeWithText("Always confirm").assertIsDisplayed()
    }
}
