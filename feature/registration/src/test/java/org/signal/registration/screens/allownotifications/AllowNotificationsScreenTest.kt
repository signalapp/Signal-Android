/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.allownotifications

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.screens.util.MockPermissionsState
import org.signal.registration.test.TestTags

/**
 * Tests for AllowNotificationsScreen that validate its callbacks.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AllowNotificationsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @OptIn(ExperimentalPermissionsApi::class)
  @Test
  fun `screen displays buttons`() {
    composeTestRule.setContent {
      SignalTheme {
        AllowNotificationsScreen(
          permissionState = MockPermissionsState(permission = Manifest.permission.POST_NOTIFICATIONS),
          onProceed = {}
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.ALLOW_NOTIFICATIONS_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.ALLOW_NOTIFICATIONS_NEXT_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.ALLOW_NOTIFICATIONS_NOT_NOW_BUTTON).assertIsDisplayed()
  }

  @OptIn(ExperimentalPermissionsApi::class)
  @Test
  fun `when Not Now is clicked, onProceed is invoked`() {
    var proceeded = false

    composeTestRule.setContent {
      SignalTheme {
        AllowNotificationsScreen(
          permissionState = MockPermissionsState(permission = Manifest.permission.POST_NOTIFICATIONS),
          onProceed = { proceeded = true }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.ALLOW_NOTIFICATIONS_NOT_NOW_BUTTON).performClick()

    assert(proceeded)
  }

  @OptIn(ExperimentalPermissionsApi::class)
  @Test
  fun `when Next is clicked with granted permission, onProceed is invoked`() {
    var proceeded = false

    composeTestRule.setContent {
      SignalTheme {
        AllowNotificationsScreen(
          permissionState = MockPermissionsState(permission = Manifest.permission.POST_NOTIFICATIONS),
          onProceed = { proceeded = true }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.ALLOW_NOTIFICATIONS_NEXT_BUTTON).performClick()

    assert(proceeded)
  }
}
