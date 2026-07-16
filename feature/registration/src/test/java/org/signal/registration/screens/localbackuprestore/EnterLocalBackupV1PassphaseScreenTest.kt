/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.localbackuprestore

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.test.TestTags

/**
 * Tests for EnterLocalBackupV1PassphaseScreen that validate callback invocations.
 * Uses Robolectric to run fast JUnit tests without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EnterLocalBackupV1PassphaseScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `screen displays input and next button`() {
    composeTestRule.setContent {
      SignalTheme {
        EnterLocalBackupV1PassphaseScreen(
          onSubmit = {},
          onCancel = {}
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.ENTER_LOCAL_BACKUP_PASSPHRASE_INPUT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.ENTER_LOCAL_BACKUP_PASSPHRASE_NEXT_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `when No passphrase is clicked, onCancel is invoked`() {
    var cancelled = false

    composeTestRule.setContent {
      SignalTheme {
        EnterLocalBackupV1PassphaseScreen(
          onSubmit = {},
          onCancel = { cancelled = true }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.ENTER_LOCAL_BACKUP_PASSPHRASE_NO_PASSPHRASE_BUTTON).performClick()

    assert(cancelled)
  }

  @Test
  fun `next is disabled initially`() {
    composeTestRule.setContent {
      SignalTheme {
        EnterLocalBackupV1PassphaseScreen(
          onSubmit = {},
          onCancel = {}
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.ENTER_LOCAL_BACKUP_PASSPHRASE_NEXT_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun `when a 30 digit passphrase is entered, next is enabled and clicking it submits`() {
    val passphrase = "012345678901234567890123456789"
    var submitted: String? = null

    composeTestRule.setContent {
      SignalTheme {
        EnterLocalBackupV1PassphaseScreen(
          onSubmit = { submitted = it },
          onCancel = {}
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.ENTER_LOCAL_BACKUP_PASSPHRASE_INPUT).performTextInput(passphrase)

    composeTestRule.onNodeWithTag(TestTags.ENTER_LOCAL_BACKUP_PASSPHRASE_NEXT_BUTTON).assertIsEnabled()
    composeTestRule.onNodeWithTag(TestTags.ENTER_LOCAL_BACKUP_PASSPHRASE_NEXT_BUTTON).performClick()

    assert(submitted == passphrase)
  }
}
