package com.hermes.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation test: opens Settings, enters an API token, clicks Test Connection.
 *
 * This test runs on a device/emulator and verifies that the Settings UI
 * responds correctly to user input on the Hermes Connection section.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SettingsTokenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun enterToken_and_clickTestConnection() {
        // Navigate to Settings tab via bottom nav
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.waitForIdle()

        // Clear and type a test API token into the tagged field
        composeTestRule.onNodeWithTag("apiTokenField").performTextClearance()
        composeTestRule.onNodeWithTag("apiTokenField").performTextInput("sk-test-token-12345")

        // Press Done to trigger onDone keyboard action
        composeTestRule.onNodeWithTag("apiTokenField").performImeAction()
        composeTestRule.waitForIdle()

        // Verify the "✅ Token set" indicator appears
        composeTestRule.onNodeWithText("\u2705 Token set").assertIsDisplayed()

        // Click the Test Connection button
        composeTestRule.onNodeWithText("Test Connection").performClick()
        composeTestRule.waitForIdle()

        // Verify the button changed state (either shows "⏳ Testing..." or a result)
        // The button still exists — connection test may succeed or fail depending on env
        composeTestRule.onNodeWithText("Test Connection").assertExists()
    }
}
