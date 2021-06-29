package org.thoughtcrime.securesms.components.settings.app.internal

import org.thoughtcrime.securesms.emoji.EmojiFiles

data class InternalSettingsState(
  val seeMoreUserDetails: Boolean,
  val gv2doNotCreateGv2Groups: Boolean,
  val gv2forceInvites: Boolean,
  val gv2ignoreServerChanges: Boolean,
  val gv2ignoreP2PChanges: Boolean,
  val disableAutoMigrationInitiation: Boolean,
  val disableAutoMigrationNotification: Boolean,
  val forceCensorship: Boolean,
  val callingServer: String,
  val useBuiltInEmojiSet: Boolean,
  val emojiVersion: EmojiFiles.Version?,
  val removeSenderKeyMinimium: Boolean,
  val delayResends: Boolean,
)
