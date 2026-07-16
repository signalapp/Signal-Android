/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalPermissionsApi::class)

package org.signal.registration.screens.permissions

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
import org.signal.registration.screens.util.MockMultiplePermissionsState
import org.signal.registration.screens.util.MockPermissionsState
import org.signal.registration.test.TestTags

/**
 * Tests for PermissionsScreen that validate its callbacks.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PermissionsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `screen displays buttons`() {
    composeTestRule.setContent {
      SignalTheme {
        PermissionsScreen(
          permissionsState = MockMultiplePermissionsState(
            permissions = listOf(
              MockPermissionsState(Manifest.permission.POST_NOTIFICATIONS),
              MockPermissionsState(Manifest.permission.READ_CONTACTS)
            )
          )
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.PERMISSIONS_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.PERMISSIONS_NEXT_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.PERMISSIONS_NOT_NOW_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `when Not Now is clicked, onProceed is invoked`() {
    var proceeded = false

    composeTestRule.setContent {
      SignalTheme {
        PermissionsScreen(
          permissionsState = MockMultiplePermissionsState(
            permissions = listOf(
              MockPermissionsState(Manifest.permission.POST_NOTIFICATIONS),
              MockPermissionsState(Manifest.permission.READ_CONTACTS)
            )
          ),
          onProceed = { proceeded = true }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.PERMISSIONS_NOT_NOW_BUTTON).performClick()

    assert(proceeded)
  }

  @Test
  fun `when Next is clicked with all permissions granted, onProceed is invoked`() {
    var proceeded = false

    composeTestRule.setContent {
      SignalTheme {
        PermissionsScreen(
          permissionsState = MockMultiplePermissionsState(
            allPermissionsGranted = true,
            permissions = listOf(
              MockPermissionsState(Manifest.permission.POST_NOTIFICATIONS),
              MockPermissionsState(Manifest.permission.READ_CONTACTS)
            )
          ),
          onProceed = { proceeded = true }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.PERMISSIONS_NEXT_BUTTON).performClick()

    assert(proceeded)
  }
}
