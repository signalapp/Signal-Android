/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.home

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface AppSettingsEvent {

  /** The screen came back to the foreground, so registration, gift badge and backup state may have changed. */
  data object ScreenResumed : AppSettingsEvent

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : AppSettingsEvent

  /** The user tapped their profile at the top of the screen. */
  data object ProfileClicked : AppSettingsEvent

  /** The user tapped the QR code button beside their username. */
  data object UsernameLinkClicked : AppSettingsEvent

  /** The user tapped the warning row shown for [AppSettingsState.backupFailureState]. */
  data object BackupWarningClicked : AppSettingsEvent

  /** The user tapped the account row. */
  data object AccountClicked : AppSettingsEvent

  /** The user tapped the linked devices row. */
  data object LinkedDevicesClicked : AppSettingsEvent

  /** The user tapped the donate row. */
  data object DonateClicked : AppSettingsEvent

  /** The user long pressed the donate row, which copies their donor subscriber id. */
  data object DonateLongClicked : AppSettingsEvent

  /** The user tapped the appearance row. */
  data object AppearanceClicked : AppSettingsEvent

  /** The user tapped the chats row. */
  data object ChatsClicked : AppSettingsEvent

  /** The user tapped the stories row. */
  data object StoriesClicked : AppSettingsEvent

  /** The user tapped the notifications row. */
  data object NotificationsClicked : AppSettingsEvent

  /** The user tapped the privacy row. */
  data object PrivacyClicked : AppSettingsEvent

  /** The user tapped the backups row. */
  data object BackupsClicked : AppSettingsEvent

  /** The user long pressed the backups row, which copies their backups subscriber id. */
  data object BackupsLongClicked : AppSettingsEvent

  /** The user tapped the data and storage row. */
  data object DataAndStorageClicked : AppSettingsEvent

  /** The user tapped the app updates row. */
  data object AppUpdatesClicked : AppSettingsEvent

  /** The user tapped the payments row. */
  data object PaymentsClicked : AppSettingsEvent

  /** The user tapped the help row. */
  data object HelpClicked : AppSettingsEvent

  /** The user tapped the invite your friends row. */
  data object InviteClicked : AppSettingsEvent

  /** The user tapped the labs row. */
  data object LabsClicked : AppSettingsEvent

  /** The user tapped the internal preferences row. */
  data object InternalClicked : AppSettingsEvent

  /** The snackbar in [AppSettingsState.snackbar] has gone away. */
  data object SnackbarDismissed : AppSettingsEvent
}
