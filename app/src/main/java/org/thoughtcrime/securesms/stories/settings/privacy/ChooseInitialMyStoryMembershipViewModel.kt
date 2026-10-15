package org.thoughtcrime.securesms.stories.settings.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stories.settings.my.MyStorySettingsRepository

class ChooseInitialMyStoryMembershipViewModel @JvmOverloads constructor(
  private val repository: MyStorySettingsRepository = MyStorySettingsRepository()
) : ViewModel() {

  private val _state = MutableStateFlow(ChooseInitialMyStoryMembershipState())

  val state: StateFlow<ChooseInitialMyStoryMembershipState> = _state.asStateFlow()

  init {
    viewModelScope.launch {
      repository.observeChooseInitialPrivacy()
        .distinctUntilChanged()
        .collect { state ->
          _state.update { state.copy(hasUserPerformedManualSelection = it.hasUserPerformedManualSelection) }
        }
    }
  }

  suspend fun select(selection: DistributionListPrivacyMode) {
    repository.setMyStoryPrivacyMode(selection)
    _state.update { it.copy(hasUserPerformedManualSelection = true) }
  }

  suspend fun save(): RecipientId {
    repository.markUserPerformedManualSelection()
    repository.notifyStorySettingsChanged()
    return _state.value.recipientId!!
  }
}
