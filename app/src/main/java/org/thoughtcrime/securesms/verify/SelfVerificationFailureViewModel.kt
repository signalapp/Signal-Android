package org.thoughtcrime.securesms.verify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogRepository

class SelfVerificationFailureViewModel : ViewModel() {

  private val submitDebugLogRepository: SubmitDebugLogRepository = SubmitDebugLogRepository()

  private val internalUiState = MutableStateFlow(VerificationUiState())
  val uiState: StateFlow<VerificationUiState> = internalUiState

  fun submitLogs() {
    viewModelScope.launch {
      internalUiState.update { it.copy(showAsProgress = true) }
      submitDebugLogRepository.buildAndSubmitLog { result ->
        internalUiState.update { it.copy(sendEmail = true, debugLogUrl = result.orNull()) }
      }
    }
  }
}

data class VerificationUiState(
  val showAsProgress: Boolean = false,
  val sendEmail: Boolean = false,
  val debugLogUrl: String? = null
)
