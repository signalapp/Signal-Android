package org.thoughtcrime.securesms.keyvalue

import org.thoughtcrime.securesms.recipients.RecipientId

class ReleaseChannelValues(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private const val KEY_RELEASE_CHANNEL_RECIPIENT_ID = "releasechannel.recipient_id"
    private const val KEY_NEXT_SCHEDULED_CHECK = "releasechannel.next_scheduled_check"
    private const val KEY_PREVIOUS_MANIFEST_MD5 = "releasechannel.previous_manifest_md5"
    private const val KEY_HIGHEST_VERSION_NOTE_RECEIVED = "releasechannel.highest_version_note_received"
    private const val KEY_MET_CONVERSATION_REQUIREMENT = "releasechannel.met_conversation_requirement"
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> = listOf(
    KEY_RELEASE_CHANNEL_RECIPIENT_ID
  )

  val releaseChannelRecipientId: RecipientId?
    get() {
      val id = getString(KEY_RELEASE_CHANNEL_RECIPIENT_ID, "")
      return if (id.isEmpty()) {
        null
      } else {
        RecipientId.from(id)
      }
    }

  fun setReleaseChannelRecipientId(id: RecipientId) {
    putString(KEY_RELEASE_CHANNEL_RECIPIENT_ID, id.serialize())
  }

  fun clearReleaseChannelRecipientId() {
    putString(KEY_RELEASE_CHANNEL_RECIPIENT_ID, "")
  }

  var nextScheduledCheck by longValue(KEY_NEXT_SCHEDULED_CHECK, 0)
  var previousManifestMd5 by blobValue(KEY_PREVIOUS_MANIFEST_MD5, ByteArray(0))
  var highestVersionNoteReceived by integerValue(KEY_HIGHEST_VERSION_NOTE_RECEIVED, 0)
  var hasMetConversationRequirement by booleanValue(KEY_MET_CONVERSATION_REQUIREMENT, false)
}
