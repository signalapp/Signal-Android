/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.theme.SignalTheme
import kotlin.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BackupsSettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val events = mutableListOf<BackupsSettingsEvent>()

  @Test
  fun whenITapClose_thenIExpectNavigateBackClickedEvent() {
    setContent()

    composeTestRule.onNodeWithContentDescription("Close").performClick()

    assertThat(events).containsExactly(BackupsSettingsEvent.NavigateBackClicked)
  }

  @Test
  fun givenNoBackups_whenITapSetUp_thenIExpectBackupsRowClickedEvent() {
    setContent()

    composeTestRule.onNodeWithText("Set up").performClick()

    assertThat(events).containsExactly(BackupsSettingsEvent.BackupsRowClicked)
  }

  @Test
  fun whenITapOnDeviceBackups_thenIExpectOnDeviceBackupsRowClickedEvent() {
    setContent()

    composeTestRule.onNodeWithText("On-device backups").performClick()

    assertThat(events).containsExactly(BackupsSettingsEvent.OnDeviceBackupsRowClicked)
  }

  @Test
  fun givenNoBackups_whenIDisplayScreen_thenIExpectTheSecureBackupsRow() {
    setContent()

    composeTestRule.onNodeWithText("Signal Secure Backups").assertIsDisplayed()
  }

  private fun setContent(state: BackupsSettingsState = BackupsSettingsState(backupState = BackupState.None, lastBackupAt = Duration.ZERO, isLinkedDevice = false)) {
    composeTestRule.setContent {
      SignalTheme {
        BackupsSettingsScreen(state = state, onEvent = { events += it })
      }
    }
  }
}
