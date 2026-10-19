package org.thoughtcrime.securesms.badges.self.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.rx3.await
import org.signal.core.util.logging.Log
import org.signal.network.NetworkResult
import org.thoughtcrime.securesms.badges.BadgeRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.RecurringInAppPaymentRepository
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.InternetConnectionObserver

private val TAG = Log.tag(BadgesOverviewViewModel::class.java)

class BadgesOverviewViewModel : ViewModel() {
  private val internalState = MutableStateFlow(BadgesOverviewState())
  private val internalEvents = Channel<BadgesOverviewEvent>(Channel.BUFFERED)

  val state: StateFlow<BadgesOverviewState> = internalState
  val events: Flow<BadgesOverviewEvent> = internalEvents.receiveAsFlow()

  init {
    viewModelScope.launch {
      Recipient.observable(Recipient.self().id).asFlow().filterNot { it.isResolving }.collect { recipient ->
        internalState.update { state ->
          state.copy(
            stage = if (state.stage == BadgesOverviewState.Stage.INIT) BadgesOverviewState.Stage.READY else state.stage,
            allUnlockedBadges = recipient.badges,
            displayBadgesOnProfile = SignalStore.inAppPayments.getDisplayBadgesOnProfile(),
            featuredBadge = recipient.featuredBadge
          )
        }
      }
    }

    viewModelScope.launch {
      InternetConnectionObserver.observe().asFlow().distinctUntilChanged().collect { isConnected ->
        internalState.update { it.copy(hasInternet = isConnected) }
      }
    }

    viewModelScope.launch {
      try {
        val (active, all) = coroutineScope {
          val activeRequest = async { RecurringInAppPaymentRepository.getActiveSubscription(InAppPaymentSubscriberRecord.Type.DONATION).await() }
          val allRequest = async { RecurringInAppPaymentRepository.getSubscriptions().await() }
          activeRequest.await() to allRequest.await()
        }

        val fadedBadgeId = if (!active.isActive && active.activeSubscription?.willCancelAtPeriodEnd == true) {
          all.firstOrNull { it.level == active.activeSubscription?.level }?.badge?.id
        } else {
          null
        }

        internalState.update { it.copy(fadedBadgeId = fadedBadgeId) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Could not retrieve data from server", e)
      }
    }
  }

  fun setDisplayBadgesOnProfile(displayBadgesOnProfile: Boolean) {
    internalState.update { it.copy(stage = BadgesOverviewState.Stage.UPDATING_BADGE_DISPLAY_STATE) }
    viewModelScope.launch {
      val result = BadgeRepository.setVisibilityForAllBadges(displayBadgesOnProfile)
      if (result !is NetworkResult.Success) {
        Log.e(TAG, "Failed to update visibility.", result.getCause())
        internalEvents.send(BadgesOverviewEvent.FAILED_TO_UPDATE_PROFILE)
      }
      internalState.update { it.copy(stage = BadgesOverviewState.Stage.READY) }
    }
  }
}
