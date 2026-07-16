package org.thoughtcrime.securesms.keyvalue

import org.thoughtcrime.securesms.util.RemoteConfig

class LabsValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {
  companion object {
    const val INDIVIDUAL_CHAT_PLAINTEXT_EXPORT: String = "labs.individual_chat_plaintext_export"
    const val STORY_ARCHIVE: String = "labs.story_archive"
    const val INCOGNITO: String = "labs.incognito"
    const val BETTER_SEARCH: String = "labs.better_search"
    const val STARRED_MESSAGES: String = "labs.starred_messages"
    const val STICKER_REPLIES: String = "labs.sticker_replies"
    const val MUTE_BREAKTHROUGH_NOTIFICATIONS: String = "labs.mute_breakthrough_notifications"
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> = emptyList()

  var individualChatPlaintextExport by booleanValue(INDIVIDUAL_CHAT_PLAINTEXT_EXPORT, true).falseForExternalUsers()

  var storyArchive by booleanValue(STORY_ARCHIVE, true).falseForExternalUsers()

  var incognito by booleanValue(INCOGNITO, true).falseForExternalUsers()

  var betterSearch by booleanValue(BETTER_SEARCH, true).falseForExternalUsers()

  var starredMessages by booleanValue(STARRED_MESSAGES, true).falseForExternalUsers()

  var stickerReplies by booleanValue(STICKER_REPLIES, false).falseForExternalUsers()

  var muteBreakthroughNotifications by booleanValue(MUTE_BREAKTHROUGH_NOTIFICATIONS, true).falseForExternalUsers()

  private fun SignalStoreValueDelegate<Boolean>.falseForExternalUsers(): SignalStoreValueDelegate<Boolean> {
    return this.map { actualValue -> RemoteConfig.internalUser && actualValue }
  }
}
