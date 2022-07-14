package org.thoughtcrime.securesms.stories.viewer.info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.rx.RxStore

/**
 * Gathers and stores the StoryInfoState which is used to render the story info sheet.
 */
class StoryInfoViewModel(storyId: Long, repository: StoryInfoRepository = StoryInfoRepository()) : ViewModel() {

  private val store = RxStore(StoryInfoState())
  private val disposables = CompositeDisposable()

  val state: Flowable<StoryInfoState> = store.stateFlowable.observeOn(AndroidSchedulers.mainThread())

  init {
    disposables += store.update(repository.getStoryInfo(storyId).toFlowable(BackpressureStrategy.LATEST)) { storyInfo, storyInfoState ->
      storyInfoState.copy(
        isLoaded = true,
        sentMillis = storyInfo.messageRecord.dateSent,
        receivedMillis = storyInfo.messageRecord.dateReceived,
        size = (storyInfo.messageRecord as? MmsMessageRecord)?.let { it.slideDeck.firstSlide?.fileSize } ?: -1L,
        isOutgoing = storyInfo.messageRecord.isOutgoing,
        recipients = buildRecipients(storyInfo)
      )
    }
  }

  private fun buildRecipients(storyInfo: StoryInfoRepository.StoryInfo): List<StoryInfoRecipientRow.Model> {
    return if (storyInfo.messageRecord.isOutgoing) {
      storyInfo.receiptInfo.map {
        StoryInfoRecipientRow.Model(
          recipient = Recipient.resolved(it.recipientId),
          date = it.timestamp,
          status = it.status
        )
      }
    } else {
      listOf(
        StoryInfoRecipientRow.Model(
          recipient = storyInfo.messageRecord.individualRecipient,
          date = storyInfo.messageRecord.dateSent,
          status = -1
        )
      )
    }
  }

  override fun onCleared() {
    disposables.clear()
  }

  class Factory(private val storyId: Long) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(StoryInfoViewModel(storyId)) as T
    }
  }
}
