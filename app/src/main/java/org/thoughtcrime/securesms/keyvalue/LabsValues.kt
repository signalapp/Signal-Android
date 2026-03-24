package org.thoughtcrime.securesms.keyvalue

import org.thoughtcrime.securesms.util.RemoteConfig

class LabsValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {
  companion object {
    const val INDIVIDUAL_CHAT_PLAINTEXT_EXPORT: String = "labs.individual_chat_plaintext_export"
    const val STORY_ARCHIVE: String = "labs.story_archive"
    const val INCOGNITO: String = "labs.incognito"
    const val GROUP_SUGGESTIONS_FOR_MEMBERS: String = "labs.group_suggestions_for_members"
    const val BETTER_SEARCH: String = "labs.better_search"
    const val AUTO_LOWER_HAND: String = "labs.auto_lower_hand"

    const val STARRED_MESSAGES: String = "labs.starred_messages"
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> = emptyList()

  var individualChatPlaintextExport by booleanValue(INDIVIDUAL_CHAT_PLAINTEXT_EXPORT, true).falseForExternalUsers()

  var storyArchive by booleanValue(STORY_ARCHIVE, true).falseForExternalUsers()

  var incognito by booleanValue(INCOGNITO, true).falseForExternalUsers()

  var groupSuggestionsForMembers by booleanValue(GROUP_SUGGESTIONS_FOR_MEMBERS, true).falseForExternalUsers()

  var betterSearch by booleanValue(BETTER_SEARCH, true).falseForExternalUsers()

  var autoLowerHand by booleanValue(AUTO_LOWER_HAND, true).falseForExternalUsers()

  var starredMessages by booleanValue(STARRED_MESSAGES, true).falseForExternalUsers()

  private fun SignalStoreValueDelegate<Boolean>.falseForExternalUsers(): SignalStoreValueDelegate<Boolean> {
    return this.map { actualValue -> RemoteConfig.internalUser && actualValue }
  }
}
