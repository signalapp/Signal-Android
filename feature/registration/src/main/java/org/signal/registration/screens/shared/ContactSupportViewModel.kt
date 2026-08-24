package org.signal.registration.screens.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.registration.ContactSupportController

/**
 * Intended to be used to drive [ContactSupportDialog]
 */
class ContactSupportViewModel(
  private val controller: ContactSupportController
) : EventDrivenViewModel<ContactSupportEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(ContactSupportViewModel::class)
  }

  private val _state = MutableStateFlow(ContactSupportState())
  val state: StateFlow<ContactSupportState> = _state.asStateFlow()

  override suspend fun processEvent(event: ContactSupportEvents) {
    when (event) {
      is ContactSupportEvents.SubmitWithDebugLog -> submitWithLogs()
      is ContactSupportEvents.SubmitWithoutDebugLog -> _state.value = ContactSupportState(sendEmail = true)
      is ContactSupportEvents.Cancel -> _state.value = ContactSupportState()
    }
  }

  private suspend fun submitWithLogs() {
    _state.value = _state.value.copy(showAsProgress = true)
    val debugLogUrl = controller.uploadDebugLog()
    if (debugLogUrl == null) {
      Log.w(TAG, "Failed to upload a debug log. Continuing without one.")
    }
    _state.value = ContactSupportState(sendEmail = true, debugLogUrl = debugLogUrl)
  }

  class Factory(private val controller: ContactSupportController) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(ContactSupportViewModel(controller)) as T
    }
  }
}
