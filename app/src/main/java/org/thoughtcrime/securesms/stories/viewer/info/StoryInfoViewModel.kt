package org.thoughtcrime.securesms.stories.viewer.info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
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
        sections = buildSections(storyInfo)
      )
    }
  }

  private fun buildSections(storyInfo: StoryInfoRepository.StoryInfo): Map<StoryInfoState.SectionKey, List<StoryInfoRecipientRow.Model>> {
    return if (storyInfo.messageRecord.isOutgoing) {
      storyInfo.receiptInfo.map { groupReceiptInfo ->
        StoryInfoRecipientRow.Model(
          recipient = Recipient.resolved(groupReceiptInfo.recipientId),
          date = groupReceiptInfo.timestamp,
          status = groupReceiptInfo.status,
          isFailed = hasFailure(storyInfo.messageRecord, groupReceiptInfo.recipientId)
        )
      }.groupBy {
        when {
          it.isFailed -> StoryInfoState.SectionKey.FAILED
          else -> StoryInfoState.SectionKey.SENT_TO
        }
      }
    } else {
      mapOf(
        StoryInfoState.SectionKey.SENT_FROM to listOf(
          StoryInfoRecipientRow.Model(
            recipient = storyInfo.messageRecord.individualRecipient,
            date = storyInfo.messageRecord.dateSent,
            status = -1,
            isFailed = false
          )
        )
      )
    }
  }

  private fun hasFailure(messageRecord: MessageRecord, recipientId: RecipientId): Boolean {
    val hasNetworkFailure = messageRecord.networkFailures.any { it.getRecipientId(ApplicationDependencies.getApplication()) == recipientId }
    val hasIdentityFailure = messageRecord.identityKeyMismatches.any { it.getRecipientId(ApplicationDependencies.getApplication()) == recipientId }

    return hasNetworkFailure || hasIdentityFailure
  }

  override fun onCleared() {
    disposables.clear()
    store.dispose()
  }

  class Factory(private val storyId: Long) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(StoryInfoViewModel(storyId)) as T
    }
  }
}
