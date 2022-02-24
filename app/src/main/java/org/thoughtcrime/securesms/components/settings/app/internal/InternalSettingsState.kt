package org.thoughtcrime.securesms.components.settings.app.internal

import org.signal.ringrtc.CallManager
import org.thoughtcrime.securesms.emoji.EmojiFiles

data class InternalSettingsState(
  val seeMoreUserDetails: Boolean,
  val shakeToReport: Boolean,
  val gv2doNotCreateGv2Groups: Boolean,
  val gv2forceInvites: Boolean,
  val gv2ignoreServerChanges: Boolean,
  val gv2ignoreP2PChanges: Boolean,
  val disableAutoMigrationInitiation: Boolean,
  val disableAutoMigrationNotification: Boolean,
  val allowCensorshipSetting: Boolean,
  val callingServer: String,
  val audioProcessingMethod: CallManager.AudioProcessingMethod,
  val useBuiltInEmojiSet: Boolean,
  val emojiVersion: EmojiFiles.Version?,
  val removeSenderKeyMinimium: Boolean,
  val delayResends: Boolean,
  val disableStorageService: Boolean,
  val disableStories: Boolean
)
