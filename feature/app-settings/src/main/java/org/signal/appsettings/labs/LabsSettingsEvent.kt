/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.labs

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface LabsSettingsEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : LabsSettingsEvent

  /** The user flipped the individual chat plaintext export toggle. */
  data class IndividualChatPlaintextExportToggled(val enabled: Boolean) : LabsSettingsEvent

  /** The user flipped the story archive toggle. */
  data class StoryArchiveToggled(val enabled: Boolean) : LabsSettingsEvent

  /** The user flipped the incognito mode toggle. */
  data class IncognitoToggled(val enabled: Boolean) : LabsSettingsEvent

  /** The user flipped the better search toggle. */
  data class BetterSearchToggled(val enabled: Boolean) : LabsSettingsEvent

  /** The user flipped the starred messages toggle. */
  data class StarredMessagesToggled(val enabled: Boolean) : LabsSettingsEvent

  /** The user flipped the sticker replies toggle. */
  data class StickerRepliesToggled(val enabled: Boolean) : LabsSettingsEvent

  /** The user flipped the improved message deletion toggle. */
  data class ImprovedMessageDeletionToggled(val enabled: Boolean) : LabsSettingsEvent
}
