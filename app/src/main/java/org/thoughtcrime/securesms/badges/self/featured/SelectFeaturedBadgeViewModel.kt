package org.thoughtcrime.securesms.badges.self.featured

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.network.NetworkResult
import org.thoughtcrime.securesms.badges.BadgeRepository
import org.thoughtcrime.securesms.recipients.Recipient

private val TAG = Log.tag(SelectFeaturedBadgeViewModel::class.java)

class SelectFeaturedBadgeViewModel : EventDrivenViewModel<SelectFeaturedBadgeEvents>(TAG) {

  private val internalState = MutableStateFlow(SelectFeaturedBadgeState())
  private val internalActions = Channel<SelectFeaturedBadgeActions>(Channel.BUFFERED)

  val state: StateFlow<SelectFeaturedBadgeState> = internalState
  val actions: Flow<SelectFeaturedBadgeActions> = internalActions.receiveAsFlow()

  init {
    viewModelScope.launch {
      Recipient.observable(Recipient.self().id).asFlow().filterNot { it.isResolving }.collect { recipient ->
        internalState.update { state ->
          val unexpiredBadges = recipient.badges.filterNot { it.isExpired() }
          state.copy(
            stage = if (state.stage == SelectFeaturedBadgeState.Stage.INIT) SelectFeaturedBadgeState.Stage.READY else state.stage,
            selectedBadge = unexpiredBadges.firstOrNull(),
            allUnlockedBadges = unexpiredBadges
          )
        }
      }
    }
  }

  override suspend fun processEvent(event: SelectFeaturedBadgeEvents) {
    when (event) {
      is SelectFeaturedBadgeEvents.BadgeSelected -> internalState.update { it.copy(selectedBadge = event.badge) }
      SelectFeaturedBadgeEvents.Save -> save()
    }
  }

  private suspend fun save() {
    val snapshot = internalState.value
    if (snapshot.stage != SelectFeaturedBadgeState.Stage.READY || snapshot.selectedBadge == null) {
      return
    }

    internalState.update { it.copy(stage = SelectFeaturedBadgeState.Stage.SAVING) }
    val result = BadgeRepository.setFeaturedBadge(snapshot.selectedBadge)
    if (result is NetworkResult.Success) {
      internalState.update { it.copy(stage = SelectFeaturedBadgeState.Stage.SAVED) }
    } else {
      Log.e(TAG, "Failed to update profile.", result.getCause())
      internalState.update { it.copy(stage = SelectFeaturedBadgeState.Stage.READY) }
      internalActions.send(SelectFeaturedBadgeActions.ShowFailedToUpdateProfile)
    }
  }
}
