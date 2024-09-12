package org.thoughtcrime.securesms.conversation.drafts

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import org.signal.core.util.Base64
import org.thoughtcrime.securesms.components.location.SignalPlace
import org.thoughtcrime.securesms.database.DraftTable.Draft
import org.thoughtcrime.securesms.database.MentionUtil
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.mms.QuoteId
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.rx.RxStore

/**
 * ViewModel responsible for holding Voice Note draft state. The intention is to allow
 * other pieces of draft state to be held here as well in the future, and to serve as a
 * management pattern going forward for drafts.
 */
class DraftViewModel @JvmOverloads constructor(
  threadId: Long = -1,
  private val repository: DraftRepository = DraftRepository()
) : ViewModel() {

  private val store = RxStore(DraftState(threadId = threadId))

  val state: Flowable<DraftState> = store.stateFlowable.observeOn(AndroidSchedulers.mainThread())

  val voiceNoteDraft: Draft?
    get() = store.state.voiceNoteDraft

  override fun onCleared() {
    store.dispose()
  }

  fun saveEphemeralVoiceNoteDraft(draft: Draft) {
    store.update { draftState ->
      saveDraftsIfChanged(draftState, draftState.copy(voiceNoteDraft = draft))
    }
  }

  fun cancelEphemeralVoiceNoteDraft(draft: Draft) {
    repository.deleteVoiceNoteDraftData(draft)
  }

  fun deleteVoiceNoteDraft() {
    store.update {
      repository.deleteVoiceNoteDraftData(it.voiceNoteDraft)
      saveDraftsIfChanged(it, it.copy(voiceNoteDraft = null))
    }
  }

  fun setMessageEditDraft(messageId: MessageId, text: String, mentions: List<Mention>, styleBodyRanges: BodyRangeList?) {
    store.update {
      val mentionRanges: BodyRangeList? = MentionUtil.mentionsToBodyRangeList(mentions)

      val bodyRanges: BodyRangeList? = if (styleBodyRanges == null) {
        mentionRanges
      } else if (mentionRanges == null) {
        styleBodyRanges
      } else {
        styleBodyRanges.newBuilder().apply { ranges += mentionRanges.ranges }.build()
      }

      saveDraftsIfChanged(it, it.copy(textDraft = text.toTextDraft(), bodyRangesDraft = bodyRanges?.toDraft(), messageEditDraft = Draft(Draft.MESSAGE_EDIT, messageId.serialize())))
    }
  }

  fun deleteMessageEditDraft() {
    store.update {
      saveDraftsIfChanged(it, it.copy(textDraft = null, bodyRangesDraft = null, messageEditDraft = null))
    }
  }

  fun setTextDraft(text: String, mentions: List<Mention>, styleBodyRanges: BodyRangeList?) {
    store.update {
      val mentionRanges: BodyRangeList? = MentionUtil.mentionsToBodyRangeList(mentions)

      val bodyRanges: BodyRangeList? = if (styleBodyRanges == null) {
        mentionRanges
      } else if (mentionRanges == null) {
        styleBodyRanges
      } else {
        styleBodyRanges.newBuilder().apply { ranges += mentionRanges.ranges }.build()
      }

      saveDraftsIfChanged(it, it.copy(textDraft = text.toTextDraft(), bodyRangesDraft = bodyRanges?.toDraft()))
    }
  }

  fun setLocationDraft(place: SignalPlace) {
    store.update {
      saveDraftsIfChanged(it, it.copy(locationDraft = Draft(Draft.LOCATION, place.serialize() ?: "")))
    }
  }

  fun clearLocationDraft() {
    store.update {
      saveDraftsIfChanged(it, it.copy(locationDraft = null))
    }
  }

  fun setQuoteDraft(id: Long, author: RecipientId) {
    store.update {
      saveDraftsIfChanged(it, it.copy(quoteDraft = Draft(Draft.QUOTE, QuoteId(id, author).serialize())))
    }
  }

  fun clearQuoteDraft() {
    store.update {
      saveDraftsIfChanged(it, it.copy(quoteDraft = null))
    }
  }

  fun clearDraft() {
    repository.deleteVoiceNoteDraftData(store.state.voiceNoteDraft)
    store.update { saveDraftsIfChanged(it, it.copyAndClearDrafts(store.state.threadId)) }
  }

  private fun saveDraftsIfChanged(oldState: DraftState, newState: DraftState): DraftState {
    if (oldState == newState) {
      return oldState
    }

    repository.saveDrafts(newState.threadId, newState.toDrafts())
    return newState
  }

  fun loadShareOrDraftData(lastShareDataTimestamp: Long): Maybe<DraftRepository.ShareOrDraftData> {
    return repository.getShareOrDraftData(lastShareDataTimestamp)
      .doOnSuccess { (_, drafts) ->
        if (drafts != null) {
          store.update { saveDraftsIfChanged(it, it.copyAndSetDrafts(drafts = drafts)) }
        }
      }
      .flatMap { (data, _) ->
        if (data == null) {
          Maybe.empty()
        } else {
          Maybe.just(data)
        }
      }
      .observeOn(AndroidSchedulers.mainThread())
  }
}

private fun String.toTextDraft(): Draft? {
  return if (isNotEmpty()) Draft(Draft.TEXT, this) else null
}

private fun BodyRangeList.toDraft(): Draft {
  return Draft(Draft.BODY_RANGES, Base64.encodeWithPadding(encode()))
}
