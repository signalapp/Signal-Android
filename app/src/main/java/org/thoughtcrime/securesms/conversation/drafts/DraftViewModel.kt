package org.thoughtcrime.securesms.conversation.drafts

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.components.voice.VoiceNoteDraft
import org.thoughtcrime.securesms.database.DraftDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture
import org.thoughtcrime.securesms.util.livedata.Store

/**
 * ViewModel responsible for holding Voice Note draft state. The intention is to allow
 * other pieces of draft state to be held here as well in the future, and to serve as a
 * management pattern going forward for drafts.
 */
class DraftViewModel(
  private val repository: DraftRepository
) : ViewModel() {

  private val store = Store<DraftState>(DraftState())

  val state: LiveData<DraftState> = store.stateLiveData

  private var voiceNoteDraftFuture: ListenableFuture<VoiceNoteDraft>? = null

  val voiceNoteDraft: DraftDatabase.Draft?
    get() = store.state.voiceNoteDraft

  fun consumeVoiceNoteDraftFuture(): ListenableFuture<VoiceNoteDraft>? {
    val future = voiceNoteDraftFuture
    voiceNoteDraftFuture = null

    return future
  }

  fun setVoiceNoteDraftFuture(voiceNoteDraftFuture: ListenableFuture<VoiceNoteDraft>) {
    this.voiceNoteDraftFuture = voiceNoteDraftFuture
  }

  fun setVoiceNoteDraft(recipientId: RecipientId, draft: DraftDatabase.Draft) {
    store.update {
      it.copy(recipientId = recipientId, voiceNoteDraft = draft)
    }
  }

  @get:JvmName("hasVoiceNoteDraft")
  val hasVoiceNoteDraft: Boolean
    get() = store.state.voiceNoteDraft != null

  fun clearVoiceNoteDraft() {
    store.update {
      it.copy(voiceNoteDraft = null)
    }
  }

  fun deleteVoiceNoteDraft() {
    val draft = store.state.voiceNoteDraft
    if (draft != null) {
      clearVoiceNoteDraft()
      repository.deleteVoiceNoteDraft(draft)
    }
  }

  fun onRecipientChanged(recipient: Recipient) {
    store.update {
      if (recipient.id != it.recipientId) {
        it.copy(recipientId = recipient.id, voiceNoteDraft = null)
      } else {
        it
      }
    }
  }

  fun deleteBlob(uri: Uri) {
    repository.deleteBlob(uri)
  }

  class Factory(private val repository: DraftRepository) : ViewModelProvider.Factory {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(DraftViewModel(repository)))
    }
  }
}
