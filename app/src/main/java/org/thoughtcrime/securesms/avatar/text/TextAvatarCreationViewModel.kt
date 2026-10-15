package org.thoughtcrime.securesms.avatar.text

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.thoughtcrime.securesms.avatar.Avatar
import org.thoughtcrime.securesms.avatar.Avatars

class TextAvatarCreationViewModel(initialText: Avatar.Text) : ViewModel() {

  private val internalState = MutableStateFlow(TextAvatarCreationState(initialText))

  val state: StateFlow<TextAvatarCreationState> = internalState

  fun setColor(colorPair: Avatars.ColorPair) {
    internalState.update { it.copy(currentAvatar = it.currentAvatar.copy(color = colorPair)) }
  }

  fun setText(text: String) {
    internalState.update {
      if (it.currentAvatar.text == text) {
        it
      } else {
        it.copy(currentAvatar = it.currentAvatar.copy(text = text))
      }
    }
  }

  fun getCurrentAvatar(): Avatar.Text {
    return internalState.value.currentAvatar
  }

  class Factory(private val initialText: Avatar.Text) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(TextAvatarCreationViewModel(initialText)))
    }
  }
}
