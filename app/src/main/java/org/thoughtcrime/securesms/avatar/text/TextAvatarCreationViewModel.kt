package org.thoughtcrime.securesms.avatar.text

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.avatar.Avatar

/**
 * Drives [TextAvatarCreationScreen], editing the text and color of [initialAvatar].
 */
class TextAvatarCreationViewModel(initialAvatar: Avatar.Text) : EventDrivenViewModel<TextAvatarCreationEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(TextAvatarCreationViewModel::class)
  }

  private val _state = MutableStateFlow(TextAvatarCreationState(initialAvatar))
  val state: StateFlow<TextAvatarCreationState> = _state.asStateFlow()

  private val _actions = Channel<TextAvatarCreationAction>(Channel.BUFFERED)
  val actions: Flow<TextAvatarCreationAction> = _actions.receiveAsFlow()

  /** Set once the editor exits, so a second tap before it leaves the screen doesn't navigate back again. */
  private var exited = false

  override suspend fun processEvent(event: TextAvatarCreationEvent) {
    when (event) {
      is TextAvatarCreationEvent.TextChanged -> _state.update { it.copy(currentAvatar = it.currentAvatar.copy(text = event.text)) }
      TextAvatarCreationEvent.KeyboardNextClicked -> _state.update { it.copy(selectedTab = TextAvatarCreationState.Tab.COLOR) }
      is TextAvatarCreationEvent.ColorSelected -> _state.update { it.copy(currentAvatar = it.currentAvatar.copy(color = event.color)) }
      is TextAvatarCreationEvent.TabClicked -> _state.update { it.copy(selectedTab = event.tab) }
      TextAvatarCreationEvent.DoneClicked -> exit(TextAvatarCreationAction.Finish(_state.value.currentAvatar))
      TextAvatarCreationEvent.NavigateBackClicked -> exit(TextAvatarCreationAction.Close)
    }
  }

  private suspend fun exit(action: TextAvatarCreationAction) {
    if (exited) {
      return
    }

    exited = true
    _actions.send(action)
  }
}
