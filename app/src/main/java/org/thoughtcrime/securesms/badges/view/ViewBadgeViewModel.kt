package org.thoughtcrime.securesms.badges.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

class ViewBadgeViewModel(
  private val startBadge: Badge?,
  recipientId: RecipientId
) : ViewModel() {

  private val internalState = MutableStateFlow(ViewBadgeState())
  val state: StateFlow<ViewBadgeState> = internalState

  init {
    viewModelScope.launch {
      Recipient.observable(recipientId).asFlow().collect { recipient ->
        internalState.update {
          it.copy(
            recipient = recipient,
            allBadgesVisibleOnProfile = recipient.badges,
            selectedBadge = startBadge ?: recipient.badges.firstOrNull(),
            badgeLoadState = ViewBadgeState.LoadState.LOADED
          )
        }
      }
    }
  }

  fun onPageSelected(position: Int) {
    if (position > internalState.value.allBadgesVisibleOnProfile.size - 1 || position < 0) {
      return
    }

    internalState.update {
      it.copy(selectedBadge = it.allBadgesVisibleOnProfile[position])
    }
  }
}
