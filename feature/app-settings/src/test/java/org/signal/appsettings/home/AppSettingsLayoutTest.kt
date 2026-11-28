/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.home

import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest
import org.signal.emoji.EmojiDependenciesRule

class AppSettingsLayoutTest : SignalLayoutStressTest() {

  companion object {
    /** The user's own name, username and about can't be reworded to fit, so they're allowed to wrap mid-word. */
    private val USER_TEXT = setOf(AppSettingsTestTags.TEXT_PROFILE_NAME, AppSettingsTestTags.TEXT_USERNAME, AppSettingsTestTags.TEXT_ABOUT)
  }

  /** The profile row renders the name and about with Emojifier. */
  @get:Rule
  val emojiDependencies = EmojiDependenciesRule(ApplicationProvider.getApplicationContext())

  private val populated = AppSettingsState(
    profile = AppSettingsState.Profile(
      name = "Alexandria Montgomery-Fitzgerald",
      formattedPhoneNumber = "+1 555-555-5555",
      username = "alexandria.montgomery.42",
      about = "Usually hiking somewhere without any signal, back on Monday"
    ),
    isPrimaryDevice = true,
    unreadPaymentsCount = 1234,
    hasExpiredGiftBadge = true,
    showInternalPreferences = true,
    showPayments = true,
    showAppUpdates = true
  )

  @Test
  fun `populated`() = assertPassesLayoutChecks(ignoreTags = USER_TEXT) {
    AppSettingsScreen(state = populated, onEvent = {})
  }

  @Test
  fun `unregistered`() = assertPassesLayoutChecks(ignoreTags = USER_TEXT) {
    AppSettingsScreen(state = populated.copy(userUnregistered = true), onEvent = {})
  }

  @Test
  fun `linked device`() = assertPassesLayoutChecks(ignoreTags = USER_TEXT) {
    AppSettingsScreen(state = populated.copy(isPrimaryDevice = false), onEvent = {})
  }

  @Test
  fun `backup could not complete`() = assertPassesLayoutChecks(ignoreTags = USER_TEXT) {
    AppSettingsScreen(state = populated.copy(backupFailureState = BackupFailureState.COULD_NOT_COMPLETE_BACKUP), onEvent = {})
  }

  @Test
  fun `backup subscription mismatch`() = assertPassesLayoutChecks(ignoreTags = USER_TEXT) {
    AppSettingsScreen(state = populated.copy(backupFailureState = BackupFailureState.SUBSCRIPTION_STATE_MISMATCH), onEvent = {})
  }

  @Test
  fun `backup already redeemed`() = assertPassesLayoutChecks(ignoreTags = USER_TEXT) {
    AppSettingsScreen(state = populated.copy(backupFailureState = BackupFailureState.ALREADY_REDEEMED), onEvent = {})
  }

  @Test
  fun `backup storage full`() = assertPassesLayoutChecks(ignoreTags = USER_TEXT) {
    AppSettingsScreen(state = populated.copy(backupFailureState = BackupFailureState.OUT_OF_STORAGE_SPACE), onEvent = {})
  }

  @Test
  fun `showing a snackbar`() = assertPassesLayoutChecks(ignoreTags = USER_TEXT) {
    AppSettingsScreen(state = populated.copy(snackbar = AppSettingsState.Snackbar.CopiedBackupsSubscriberId), onEvent = {})
  }
}
