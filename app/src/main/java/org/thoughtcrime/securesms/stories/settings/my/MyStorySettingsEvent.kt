/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stories.settings.my

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface MyStorySettingsEvent {

  /** The screen came back to the foreground, so anything we read out of the database may be stale. */
  data object ScreenResumed : MyStorySettingsEvent

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : MyStorySettingsEvent

  /** The user selected the "All Signal connections" row. */
  data object AllSignalConnectionsClicked : MyStorySettingsEvent

  /** The user tapped "View" on the "All Signal connections" row. */
  data object ViewAllSignalConnectionsClicked : MyStorySettingsEvent

  /** The user selected the "All except..." row. */
  data object AllExceptClicked : MyStorySettingsEvent

  /** The user selected the "Only share with..." row. */
  data object OnlyShareWithClicked : MyStorySettingsEvent

  /** The user tapped the "Learn more" link under the privacy options. */
  data object LearnMoreClicked : MyStorySettingsEvent

  /** The user flipped the replies and reactions switch. */
  data class RepliesAndReactionsToggled(val enabled: Boolean) : MyStorySettingsEvent
}
