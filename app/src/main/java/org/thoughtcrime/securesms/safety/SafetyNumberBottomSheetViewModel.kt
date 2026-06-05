package org.thoughtcrime.securesms.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.awaitSingleOrNull
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.ui.error.SafetyNumberChangeRepository
import org.thoughtcrime.securesms.conversation.ui.error.TrustAndVerifyResult
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.recipients.RecipientId

/** ViewModel for [SafetyNumberBottomSheetFragment]. Manages state and trust-and-verify logic. */
class SafetyNumberBottomSheetViewModel(
  private val args: SafetyNumberBottomSheetArgs,
  private val repository: SafetyNumberBottomSheetRepository = SafetyNumberBottomSheetRepository(),
  private val trustAndVerifyRepository: SafetyNumberChangeRepository = SafetyNumberChangeRepository()
) : ViewModel() {

  companion object {
    private const val MAX_RECIPIENTS_TO_DISPLAY = 5
  }

  private val destinations = MutableStateFlow(args.destinations)

  /** Point-in-time snapshot of send destinations, read after trust-and-verify completes. */
  val destinationSnapshot: List<ContactSearchKey.RecipientSearchKey>
    get() = destinations.value

  private val internalState: MutableStateFlow<SafetyNumberBottomSheetState> = MutableStateFlow(
    SafetyNumberBottomSheetState(
      untrustedRecipientCount = args.untrustedRecipients.size,
      hasLargeNumberOfUntrustedRecipients = args.untrustedRecipients.size > MAX_RECIPIENTS_TO_DISPLAY
    )
  )
  val state: StateFlow<SafetyNumberBottomSheetState> = internalState.asStateFlow()

  private val internalEffects = MutableSharedFlow<SafetyNumberBottomSheetEffect>(extraBufferCapacity = 1)
  val effects = internalEffects.asSharedFlow()

  val sendAnywayFired: Boolean
    get() = internalState.value.sendAnywayFired

  init {
    viewModelScope.launch {
      destinations
        .flatMapLatest { repository.getBuckets(args.untrustedRecipients, it).asFlow() }
        .collect { map ->
          internalState.update { state ->
            state.copy(
              destinationToRecipientMap = map,
              untrustedRecipientCount = map.size,
              loadState = if (state.loadState == SafetyNumberBottomSheetState.LoadState.INIT) SafetyNumberBottomSheetState.LoadState.READY else state.loadState
            )
          }
        }
    }
  }

  fun setDone() {
    internalState.update { it.copy(loadState = SafetyNumberBottomSheetState.LoadState.DONE) }
  }

  fun onEvent(event: SafetyNumberBottomSheetEvent) {
    when (event) {
      SafetyNumberBottomSheetEvent.SendAnyway -> {
        if (internalState.value.sendAnywayFired) return
        internalState.update { it.copy(sendAnywayFired = true) }
        viewModelScope.launch {
          val result = trustAndVerify()
          internalEffects.tryEmit(SafetyNumberBottomSheetEffect.TrustCompleted(result, destinationSnapshot))
        }
      }
      SafetyNumberBottomSheetEvent.ReviewConnections -> setDone()
      is SafetyNumberBottomSheetEvent.VerifySafetyNumber -> Unit
      is SafetyNumberBottomSheetEvent.RemoveFromStory -> removeRecipientFromSelectedStories(event.recipientId)
      is SafetyNumberBottomSheetEvent.RemoveDestination -> removeDestination(event.recipientId)
      is SafetyNumberBottomSheetEvent.RemoveAll -> removeAll(event.bucket)
    }
  }

  /** Fetches the current [IdentityRecord] for [recipientId], or null if none exists. */
  suspend fun getIdentityRecord(recipientId: RecipientId): IdentityRecord? {
    return repository.getIdentityRecord(recipientId).awaitSingleOrNull()
  }

  fun removeRecipientFromSelectedStories(recipientId: RecipientId) {
    viewModelScope.launch {
      repository.removeFromStories(recipientId, destinations.value).await()
    }
  }

  fun removeDestination(destination: RecipientId) {
    destinations.update { list -> list.filterNot { it.recipientId == destination } }
  }

  fun removeAll(distributionListBucket: SafetyNumberBucket.DistributionListBucket) {
    val toRemove = internalState.value.destinationToRecipientMap[distributionListBucket] ?: return
    viewModelScope.launch {
      repository.removeAllFromStory(toRemove.map { it.recipient.id }, distributionListBucket.distributionListId).await()
    }
  }

  private suspend fun trustAndVerify(): TrustAndVerifyResult {
    val recipients = internalState.value.destinationToRecipientMap.values.flatten().distinct()
    return if (args.messageId != null) {
      trustAndVerifyRepository.trustOrVerifyChangedRecipientsAndResendRx(recipients, args.messageId).await()
    } else {
      trustAndVerifyRepository.trustOrVerifyChangedRecipientsRx(recipients).await()
    }
  }
}
