package org.thoughtcrime.securesms.components.settings.app.usernamelinks.colorpicker

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.usernamecolorpicker.UsernameLinkQrColorPickerEvent
import org.signal.appsettings.usernamecolorpicker.UsernameLinkQrColorPickerScreen
import org.signal.appsettings.usernamecolorpicker.UsernameLinkQrColorPickerState
import org.signal.appsettings.usernamelinks.QrCodeState
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log

/**
 * Drives [UsernameLinkQrColorPickerScreen].
 */
class UsernameLinkQrColorPickerViewModel(
  private val parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) : EventDrivenViewModel<UsernameLinkQrColorPickerEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(UsernameLinkQrColorPickerViewModel::class)
  }

  private val _state = MutableStateFlow(
    UsernameLinkQrColorPickerState(
      username = UsernameLinkQrColorPickerRepository.getUsername(),
      selectedColorScheme = UsernameLinkQrColorPickerRepository.getColorScheme()
    )
  )

  val state: StateFlow<UsernameLinkQrColorPickerState> = _state.asStateFlow()

  init {
    viewModelScope.launch {
      val qrCodeData = UsernameLinkQrColorPickerRepository.getQrCodeData()
      _state.update { it.copy(qrCodeData = if (qrCodeData != null) QrCodeState.Present(qrCodeData) else QrCodeState.NotSet) }
    }
  }

  override suspend fun processEvent(event: UsernameLinkQrColorPickerEvent) {
    when (event) {
      UsernameLinkQrColorPickerEvent.NavigateBackClicked -> parentEventEmitter.navigateBack()
      UsernameLinkQrColorPickerEvent.DoneClicked -> parentEventEmitter.navigateBack()
      is UsernameLinkQrColorPickerEvent.ColorSelected -> {
        _state.update { it.copy(selectedColorScheme = event.colorScheme) }
        UsernameLinkQrColorPickerRepository.setColorScheme(event.colorScheme)
      }
    }
  }
}
