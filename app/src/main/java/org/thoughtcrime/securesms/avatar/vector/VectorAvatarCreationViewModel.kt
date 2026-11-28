package org.thoughtcrime.securesms.avatar.vector

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
 * Drives [VectorAvatarCreationScreen], recoloring [initialAvatar].
 */
class VectorAvatarCreationViewModel(initialAvatar: Avatar.Vector) : EventDrivenViewModel<VectorAvatarCreationEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(VectorAvatarCreationViewModel::class)
  }

  private val _state = MutableStateFlow(VectorAvatarCreationState(initialAvatar))
  val state: StateFlow<VectorAvatarCreationState> = _state.asStateFlow()

  private val _actions = Channel<VectorAvatarCreationAction>(Channel.BUFFERED)
  val actions: Flow<VectorAvatarCreationAction> = _actions.receiveAsFlow()

  /** Set once the editor exits, so a second tap before it leaves the screen doesn't navigate back again. */
  private var exited = false

  override suspend fun processEvent(event: VectorAvatarCreationEvent) {
    when (event) {
      is VectorAvatarCreationEvent.ColorSelected -> _state.update { it.copy(currentAvatar = it.currentAvatar.copy(color = event.color)) }
      VectorAvatarCreationEvent.DoneClicked -> exit(VectorAvatarCreationAction.Finish(_state.value.currentAvatar))
      VectorAvatarCreationEvent.NavigateBackClicked -> exit(VectorAvatarCreationAction.Close)
    }
  }

  private suspend fun exit(action: VectorAvatarCreationAction) {
    if (exited) {
      return
    }

    exited = true
    _actions.send(action)
  }
}
