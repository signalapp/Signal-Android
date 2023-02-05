package org.thoughtcrime.securesms.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.ui.error.SafetyNumberChangeRepository
import org.thoughtcrime.securesms.conversation.ui.error.TrustAndVerifyResult
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.rx.RxStore

class SafetyNumberBottomSheetViewModel(
  private val args: SafetyNumberBottomSheetArgs,
  private val repository: SafetyNumberBottomSheetRepository = SafetyNumberBottomSheetRepository(),
  private val trustAndVerifyRepository: SafetyNumberChangeRepository
) : ViewModel() {

  companion object {
    private const val MAX_RECIPIENTS_TO_DISPLAY = 5
  }

  private val destinationStore = RxStore(args.destinations)
  val destinationSnapshot: List<ContactSearchKey.RecipientSearchKey>
    get() = destinationStore.state

  private val store = RxStore(
    SafetyNumberBottomSheetState(
      untrustedRecipientCount = args.untrustedRecipients.size,
      hasLargeNumberOfUntrustedRecipients = args.untrustedRecipients.size > MAX_RECIPIENTS_TO_DISPLAY
    )
  )

  val state: Flowable<SafetyNumberBottomSheetState> = store.stateFlowable.observeOn(AndroidSchedulers.mainThread())

  private val disposables = CompositeDisposable()

  init {
    val bucketFlowable: Flowable<Map<SafetyNumberBucket, List<SafetyNumberRecipient>>> = destinationStore.stateFlowable.switchMap { repository.getBuckets(args.untrustedRecipients, it) }
    disposables += store.update(bucketFlowable) { map, state ->
      state.copy(
        destinationToRecipientMap = map,
        untrustedRecipientCount = map.size,
        loadState = if (state.loadState == SafetyNumberBottomSheetState.LoadState.INIT) SafetyNumberBottomSheetState.LoadState.READY else state.loadState
      )
    }
  }

  fun setDone() {
    store.update { it.copy(loadState = SafetyNumberBottomSheetState.LoadState.DONE) }
  }

  fun trustAndVerify(): Single<TrustAndVerifyResult> {
    val recipients: List<SafetyNumberRecipient> = store.state.destinationToRecipientMap.values.flatten().distinct()
    return if (args.messageId != null) {
      trustAndVerifyRepository.trustOrVerifyChangedRecipientsAndResendRx(recipients, args.messageId)
    } else {
      trustAndVerifyRepository.trustOrVerifyChangedRecipientsRx(recipients).observeOn(AndroidSchedulers.mainThread())
    }
  }

  override fun onCleared() {
    disposables.clear()
    destinationStore.dispose()
    store.dispose()
  }

  fun getIdentityRecord(recipientId: RecipientId): Maybe<IdentityRecord> {
    return repository.getIdentityRecord(recipientId).observeOn(AndroidSchedulers.mainThread())
  }

  fun removeRecipientFromSelectedStories(recipientId: RecipientId) {
    disposables += repository.removeFromStories(recipientId, destinationStore.state).subscribe()
  }

  fun removeDestination(destination: RecipientId) {
    destinationStore.update { list -> list.filterNot { it.recipientId == destination } }
  }

  fun removeAll(distributionListBucket: SafetyNumberBucket.DistributionListBucket) {
    val toRemove = store.state.destinationToRecipientMap[distributionListBucket] ?: return
    disposables += repository.removeAllFromStory(toRemove.map { it.recipient.id }, distributionListBucket.distributionListId).subscribe()
  }

  class Factory(
    private val args: SafetyNumberBottomSheetArgs,
    private val trustAndVerifyRepository: SafetyNumberChangeRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(SafetyNumberBottomSheetViewModel(args = args, trustAndVerifyRepository = trustAndVerifyRepository)) as T
    }
  }
}
