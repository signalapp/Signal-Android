/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.restoreselection

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.test.TestTags

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ArchiveRestoreSelectionScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val allOptionsState = ArchiveRestoreSelectionState(
    restoreOptions = listOf(
      ArchiveRestoreOption.SignalSecureBackup,
      ArchiveRestoreOption.LocalBackup,
      ArchiveRestoreOption.DeviceTransfer,
      ArchiveRestoreOption.None
    )
  )

  @Test
  fun `screen displays all option cards`() {
    composeTestRule.setContent {
      SignalTheme {
        ArchiveRestoreSelectionScreen(
          state = allOptionsState,
          onEvent = {}
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.ARCHIVE_RESTORE_SELECTION_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FOLDER).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.ARCHIVE_RESTORE_SELECTION_DEVICE_TRANSFER).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.ARCHIVE_RESTORE_SELECTION_NONE).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `when Signal Backups card is clicked, RestoreOptionSelected SignalSecureBackup is emitted`() {
    var emittedEvent: ArchiveRestoreSelectionScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        ArchiveRestoreSelectionScreen(
          state = allOptionsState,
          onEvent = { emittedEvent = it }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS).performScrollTo().performClick()

    assert(emittedEvent == ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected(ArchiveRestoreOption.SignalSecureBackup))
  }

  @Test
  fun `when Device Transfer card is clicked, RestoreOptionSelected DeviceTransfer is emitted`() {
    var emittedEvent: ArchiveRestoreSelectionScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        ArchiveRestoreSelectionScreen(
          state = allOptionsState,
          onEvent = { emittedEvent = it }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.ARCHIVE_RESTORE_SELECTION_DEVICE_TRANSFER).performScrollTo().performClick()

    assert(emittedEvent == ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected(ArchiveRestoreOption.DeviceTransfer))
  }

  @Test
  fun `when Backup Folder card is clicked, RestoreOptionSelected LocalBackup is emitted`() {
    var emittedEvent: ArchiveRestoreSelectionScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        ArchiveRestoreSelectionScreen(
          state = allOptionsState,
          onEvent = { emittedEvent = it }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FOLDER).performScrollTo().performClick()

    assert(emittedEvent == ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected(ArchiveRestoreOption.LocalBackup))
  }

  @Test
  fun `when None card is clicked, RestoreOptionSelected None is emitted`() {
    var emittedEvent: ArchiveRestoreSelectionScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        ArchiveRestoreSelectionScreen(
          state = allOptionsState,
          onEvent = { emittedEvent = it }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.ARCHIVE_RESTORE_SELECTION_NONE).performScrollTo().performClick()

    assert(emittedEvent == ArchiveRestoreSelectionScreenEvents.RestoreOptionSelected(ArchiveRestoreOption.None))
  }
}
