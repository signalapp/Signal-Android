package org.thoughtcrime.securesms.conversation.drafts

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import org.thoughtcrime.securesms.components.location.SignalPlace
import org.thoughtcrime.securesms.components.voice.VoiceNoteDraft
import org.thoughtcrime.securesms.database.DraftTable.Draft
import org.thoughtcrime.securesms.database.MentionUtil
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.mms.QuoteId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.Base64
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture
import org.thoughtcrime.securesms.util.rx.RxStore

/**
 * ViewModel responsible for holding Voice Note draft state. The intention is to allow
 * other pieces of draft state to be held here as well in the future, and to serve as a
 * management pattern going forward for drafts.
 */
class DraftViewModel @JvmOverloads constructor(
  private val repository: DraftRepository = DraftRepository()
) : ViewModel() {

  private val store = RxStore(DraftState())

  val state: Flowable<DraftState> = store.stateFlowable.observeOn(AndroidSchedulers.mainThread())

  val voiceNoteDraft: Draft?
    get() = store.state.voiceNoteDraft

  override fun onCleared() {
    store.dispose()
  }

  fun setThreadId(threadId: Long) {
    store.update { it.copy(threadId = threadId) }
  }

  fun setDistributionType(distributionType: Int) {
    store.update { it.copy(distributionType = distributionType) }
  }

  fun saveEphemeralVoiceNoteDraft(voiceNoteDraftFuture: ListenableFuture<VoiceNoteDraft>) {
    store.update {
      saveDrafts(it.copy(voiceNoteDraft = voiceNoteDraftFuture.get().asDraft()))
    }
  }

  fun cancelEphemeralVoiceNoteDraft(draft: Draft) {
    repository.deleteVoiceNoteDraftData(draft)
  }

  fun deleteVoiceNoteDraft() {
    store.update {
      repository.deleteVoiceNoteDraftData(it.voiceNoteDraft)
      saveDrafts(it.copy(voiceNoteDraft = null))
    }
  }

  fun onRecipientChanged(recipient: Recipient) {
    store.update { it.copy(recipientId = recipient.id) }
  }

  fun setTextDraft(text: String, mentions: List<Mention>) {
    store.update {
      saveDrafts(it.copy(textDraft = text.toTextDraft(), mentionsDraft = mentions.toMentionsDraft()))
    }
  }

  fun setLocationDraft(place: SignalPlace) {
    store.update {
      saveDrafts(it.copy(locationDraft = Draft(Draft.LOCATION, place.serialize() ?: "")))
    }
  }

  fun clearLocationDraft() {
    store.update {
      saveDrafts(it.copy(locationDraft = null))
    }
  }

  fun setQuoteDraft(id: Long, author: RecipientId) {
    store.update {
      saveDrafts(it.copy(quoteDraft = Draft(Draft.QUOTE, QuoteId(id, author).serialize())))
    }
  }

  fun clearQuoteDraft() {
    store.update {
      saveDrafts(it.copy(quoteDraft = null))
    }
  }

  fun onSendComplete(threadId: Long) {
    repository.deleteVoiceNoteDraftData(store.state.voiceNoteDraft)
    store.update { saveDrafts(it.copyAndClearDrafts(threadId)) }
  }

  private fun saveDrafts(state: DraftState): DraftState {
    repository.saveDrafts(Recipient.resolved(state.recipientId), state.threadId, state.distributionType, state.toDrafts())
    return state
  }

  fun loadDrafts(threadId: Long): Single<DraftRepository.DatabaseDraft> {
    return repository
      .loadDrafts(threadId)
      .doOnSuccess { drafts ->
        store.update { saveDrafts(it.copyAndSetDrafts(threadId, drafts.drafts)) }
      }
      .observeOn(AndroidSchedulers.mainThread())
  }
}

private fun String.toTextDraft(): Draft? {
  return if (isNotEmpty()) Draft(Draft.TEXT, this) else null
}

private fun List<Mention>.toMentionsDraft(): Draft? {
  val mentions: BodyRangeList? = MentionUtil.mentionsToBodyRangeList(this)
  return if (mentions != null) {
    Draft(Draft.MENTION, Base64.encodeBytes(mentions.toByteArray()))
  } else {
    null
  }
}
