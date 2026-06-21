package com.hermes.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Expanded instrumentation tests verifying Chat message flow, model switching,
 * and the /secure privileged command dialog.
 *
 * Runs on an emulator or device. Assumes the app starts on the Chat tab.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ChatAndSettingsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun sendMessage_showsAssistantBubble() {
        // The app starts on the Chat tab by default (startDestination = Routes.Chat).
        // Compose UI testing type text into the Message input
        composeTestRule.onNodeWithContentDescription("Send").assertExists()

        // Type into the input field
        composeTestRule.onNodeWithText("Message").performTextInput("hello")
        composeTestRule.waitForIdle()

        // Send the message
        composeTestRule.onNodeWithContentDescription("Send").performClick()
        composeTestRule.waitForIdle()

        // An assistant bubble should appear — either pending ("⏳ Sending...")
        // or after retries it shows "⚠️ Failed — tap to retry". Either proves
        // the send flow executed and rendered a response bubble.
        val assistantBubbleVisible = try {
            composeTestRule.onNodeWithText("\u23F3 Sending...").assertIsDisplayed()
            true
        } catch (_: AssertionError) {
            false
        }
        val failedBubbleVisible = try {
            composeTestRule.onNodeWithText("\u26A0\uFE0F Failed \u2014 tap to retry").assertIsDisplayed()
            true
        } catch (_: AssertionError) {
            false
        }

        check(assistantBubbleVisible || failedBubbleVisible) {
            "Expected an assistant bubble (pending or failed) after sending a message"
        }
    }

    @Test
    fun switchModel_showsSystemMessage() {
        // Navigate to Settings tab via bottom nav
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.waitForIdle()

        // Click the Pro model radio option
        composeTestRule.onNodeWithText("Pro (deepseek-v4-pro)").performClick()
        composeTestRule.waitForIdle()

        // Navigate back to Chat tab
        composeTestRule.onNodeWithText("Chat").performClick()
        composeTestRule.waitForIdle()

        // Verify the system message about the model switch appears
        composeTestRule.onNodeWithText("\u2705 Switched to **Pro (deepseek-v4-pro)**")
            .assertIsDisplayed()
    }

    @Test
    fun sendSecureCommand_showsPrivilegedDialog() {
        // The app starts on Chat tab. Type /secure into the input.
        composeTestRule.onNodeWithText("Message").performTextInput("/secure")
        composeTestRule.waitForIdle()

        // Send the message
        composeTestRule.onNodeWithContentDescription("Send").performClick()
        composeTestRule.waitForIdle()

        // The SecurePromptDialog should appear with its title
        composeTestRule.onNodeWithText("\uD83D\uDD12 Privileged Command").assertIsDisplayed()

        // Dismiss the dialog via the Cancel button for cleanup
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()
    }
}
