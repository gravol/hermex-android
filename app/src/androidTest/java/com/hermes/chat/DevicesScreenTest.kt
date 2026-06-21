package com.hermes.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation test: navigates to the Devices tab and verifies
 * the Clerk device card is rendered.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class DevicesScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigateToDevices_showsClerkCard() {
        // Navigate to Devices tab via bottom nav
        composeTestRule.onNodeWithText("Devices").performClick()
        composeTestRule.waitForIdle()

        // Verify the Clerk card header is visible
        composeTestRule.onNodeWithText("Clerk").assertIsDisplayed()
    }
}
