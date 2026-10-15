package org.thoughtcrime.securesms.avatar.vector

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.Avatars

class VectorAvatarCreationViewModel(initialAvatar: Avatar.Vector) : ViewModel() {

  private val internalState = MutableStateFlow(VectorAvatarCreationState(initialAvatar))

  val state: StateFlow<VectorAvatarCreationState> = internalState

  fun setColor(colorPair: Avatars.ColorPair) {
    internalState.update { it.copy(currentAvatar = it.currentAvatar.copy(color = colorPair)) }
  }

  fun getCurrentAvatar() = internalState.value.currentAvatar
}
