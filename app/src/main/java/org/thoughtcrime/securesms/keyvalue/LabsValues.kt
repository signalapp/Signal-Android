package org.thoughtcrime.securesms.keyvalue

import org.thoughtcrime.securesms.util.RemoteConfig

class LabsValues internal constructor(store: KeyValueStore) : SignalStoreValues(store) {
  companion object {
    const val INDIVIDUAL_CHAT_PLAINTEXT_EXPORT: String = "labs.individual_chat_plaintext_export"
    const val STORY_ARCHIVE: String = "labs.story_archive"
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> = emptyList()

  var individualChatPlaintextExport by booleanValue(INDIVIDUAL_CHAT_PLAINTEXT_EXPORT, true).falseForExternalUsers()

  var storyArchive by booleanValue(STORY_ARCHIVE, true).falseForExternalUsers()

  private fun SignalStoreValueDelegate<Boolean>.falseForExternalUsers(): SignalStoreValueDelegate<Boolean> {
    return this.map { actualValue -> RemoteConfig.internalUser && actualValue }
  }
}
