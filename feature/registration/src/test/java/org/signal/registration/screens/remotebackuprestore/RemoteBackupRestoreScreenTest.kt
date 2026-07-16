/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.remotebackuprestore

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.registration.test.TestTags

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RemoteBackupRestoreScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val loadedState = RemoteBackupRestoreState(
    aep = AccountEntropyPool("0000000000000000000000000000000000000000000000000000000000000000"),
    loadState = RemoteBackupRestoreState.LoadState.Loaded,
    backupTime = System.currentTimeMillis(),
    backupSize = 1234567
  )

  @Test
  fun `screen displays restore and cancel buttons`() {
    composeTestRule.setContent {
      SignalTheme {
        RemoteRestoreScreen(
          state = loadedState,
          onEvent = {}
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.REMOTE_BACKUP_RESTORE_RESTORE_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(TestTags.REMOTE_BACKUP_RESTORE_CANCEL_BUTTON).assertIsDisplayed()
  }

  @Test
  fun `when Restore is clicked, BackupRestoreBackup event is emitted`() {
    var emittedEvent: RemoteBackupRestoreScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        RemoteRestoreScreen(
          state = loadedState,
          onEvent = { emittedEvent = it }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.REMOTE_BACKUP_RESTORE_RESTORE_BUTTON).performClick()

    assert(emittedEvent == RemoteBackupRestoreScreenEvents.BackupRestoreBackup)
  }

  @Test
  fun `when Cancel is clicked, Cancel event is emitted`() {
    var emittedEvent: RemoteBackupRestoreScreenEvents? = null

    composeTestRule.setContent {
      SignalTheme {
        RemoteRestoreScreen(
          state = loadedState,
          onEvent = { emittedEvent = it }
        )
      }
    }

    composeTestRule.onNodeWithTag(TestTags.REMOTE_BACKUP_RESTORE_CANCEL_BUTTON).performClick()

    assert(emittedEvent == RemoteBackupRestoreScreenEvents.Cancel)
  }
}
