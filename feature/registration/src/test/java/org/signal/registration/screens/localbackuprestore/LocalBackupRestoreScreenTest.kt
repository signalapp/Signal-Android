/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.localbackuprestore

import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import org.signal.registration.R
import org.signal.registration.screens.shared.RestoreProgress
import org.signal.registration.test.TestTags
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocalBackupRestoreScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  @Test
  fun `SelectFolder phase displays screen and select folder button`() {
    composeTestRule.setContent {
      SignalTheme {
        LocalBackupRestoreScreen(
          state = LocalBackupRestoreState(restorePhase = LocalBackupRestoreState.RestorePhase.SelectFolder),
          onEvent = {}
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.LOCAL_BACKUP_RESTORE_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.LOCAL_BACKUP_RESTORE_SELECT_FOLDER_BUTTON).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `when select folder button is clicked, PickBackupFolder is emitted`() {
    var emittedEvent: LocalBackupRestoreEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        LocalBackupRestoreScreen(
          state = LocalBackupRestoreState(restorePhase = LocalBackupRestoreState.RestorePhase.SelectFolder),
          onEvent = { emittedEvent = it }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.LOCAL_BACKUP_RESTORE_SELECT_FOLDER_BUTTON).performScrollTo().performClick()

    assert(emittedEvent == LocalBackupRestoreEvents.PickBackupFolder)
  }

  @Test
  fun `BackupFound phase displays restore button and clicking it emits RestoreBackup`() {
    var emittedEvent: LocalBackupRestoreEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        LocalBackupRestoreScreen(
          state = LocalBackupRestoreState(
            restorePhase = LocalBackupRestoreState.RestorePhase.BackupFound,
            backupInfo = LocalBackupInfo(
              type = LocalBackupInfo.BackupType.V2,
              date = LocalDateTime.of(2026, 3, 15, 14, 30, 0),
              name = "signal-backup-2026-03-15-14-30-00",
              uri = Uri.EMPTY,
              sizeBytes = 1_482_184_499
            )
          ),
          onEvent = { emittedEvent = it }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.LOCAL_BACKUP_RESTORE_RESTORE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.LOCAL_BACKUP_RESTORE_RESTORE_BUTTON).performClick()

    assert(emittedEvent == LocalBackupRestoreEvents.RestoreBackup)
  }

  @Test
  fun `V1 InProgress phase displays progress bar`() {
    composeTestRule.setContent {
      SignalTheme {
        LocalBackupRestoreScreen(
          state = LocalBackupRestoreState(
            restorePhase = LocalBackupRestoreState.RestorePhase.InProgress,
            backupInfo = LocalBackupInfo(
              type = LocalBackupInfo.BackupType.V1,
              date = LocalDateTime.of(2026, 3, 15, 14, 30, 0),
              name = "signal-2026-03-15-14-30-00.backup",
              uri = Uri.EMPTY,
              sizeBytes = 1_482_184_499
            ),
            progressFraction = 0.5f
          ),
          onEvent = {}
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.LOCAL_BACKUP_RESTORE_PROGRESS_BAR).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun `V2 InProgress phase displays restore progress dialog and not the linear progress bar`() {
    val context = ApplicationProvider.getApplicationContext<Application>()

    composeTestRule.setContent {
      SignalTheme {
        LocalBackupRestoreScreen(
          state = LocalBackupRestoreState(
            restorePhase = LocalBackupRestoreState.RestorePhase.InProgress,
            backupInfo = LocalBackupInfo(
              type = LocalBackupInfo.BackupType.V2,
              date = LocalDateTime.of(2026, 3, 15, 14, 30, 0),
              name = "signal-backup-2026-03-15-14-30-00",
              uri = Uri.EMPTY,
              sizeBytes = 1_482_184_499
            ),
            restoreProgress = RestoreProgress(
              phase = RestoreProgress.Phase.Restoring,
              bytesCompleted = 50,
              totalBytes = 100
            )
          ),
          onEvent = {}
        )
      }
    }

    composeTestRule.onNodeWithText(context.getString(R.string.RemoteRestoreScreen__restoring_messages)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.LOCAL_BACKUP_RESTORE_PROGRESS_BAR).assertIsNotDisplayed()
  }
}
