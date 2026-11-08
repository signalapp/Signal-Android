package org.thoughtcrime.securesms.stories.settings.my

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode

/**
 * Drives [MyStorySettingsScreen].
 */
class MyStorySettingsViewModel(
  private val repository: MyStorySettingsRepository = MyStorySettingsRepository()
) : EventDrivenViewModel<MyStorySettingsEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(MyStorySettingsViewModel::class)
  }

  private val _state = MutableStateFlow(MyStorySettingsState(hasUserPerformedManualSelection = repository.hasUserPerformedManualSelection()))
  private val _actions = Channel<MyStorySettingsAction>(Channel.BUFFERED)

  val state: StateFlow<MyStorySettingsState> = _state.asStateFlow()
  val actions: Flow<MyStorySettingsAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: MyStorySettingsEvent) {
    when (event) {
      MyStorySettingsEvent.ScreenResumed -> {
        refresh()
      }
      MyStorySettingsEvent.NavigateBackClicked -> {
        _actions.send(MyStorySettingsAction.NavigateBack)
      }
      MyStorySettingsEvent.AllSignalConnectionsClicked -> {
        selectPrivacyMode(DistributionListPrivacyMode.ALL)
      }
      MyStorySettingsEvent.ViewAllSignalConnectionsClicked -> {
        _actions.send(MyStorySettingsAction.ShowAllSignalConnections)
      }
      MyStorySettingsEvent.AllExceptClicked -> {
        selectPrivacyMode(DistributionListPrivacyMode.ALL_EXCEPT)
        _actions.send(MyStorySettingsAction.NavigateToAllExcept)
      }
      MyStorySettingsEvent.OnlyShareWithClicked -> {
        selectPrivacyMode(DistributionListPrivacyMode.ONLY_WITH)
        _actions.send(MyStorySettingsAction.NavigateToOnlyShareWith)
      }
      MyStorySettingsEvent.LearnMoreClicked -> {
        _actions.send(MyStorySettingsAction.ShowSignalConnectionsEducation)
      }
      is MyStorySettingsEvent.RepliesAndReactionsToggled -> {
        repository.setRepliesAndReactionsEnabled(event.enabled)
        refresh()
      }
    }
  }

  private suspend fun selectPrivacyMode(privacyMode: DistributionListPrivacyMode) {
    _state.update { it.copy(hasUserPerformedManualSelection = true) }
    repository.markUserPerformedManualSelection()

    if (privacyMode == _state.value.myStoryPrivacyState.privacyMode) {
      repository.notifyStorySettingsChanged()
    } else {
      repository.setMyStoryPrivacyMode(privacyMode)
      refresh()
    }
  }

  private suspend fun refresh() {
    val privacyState = repository.getPrivacyState()
    val repliesAndReactionsEnabled = repository.getRepliesAndReactionsEnabled()
    val allSignalConnectionsCount = repository.getAllSignalConnectionsCount()

    _state.update {
      it.copy(
        myStoryPrivacyState = privacyState,
        areRepliesAndReactionsEnabled = repliesAndReactionsEnabled,
        allSignalConnectionsCount = allSignalConnectionsCount
      )
    }
  }
}
