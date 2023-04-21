package org.thoughtcrime.securesms.calls.links.details

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.database.CallLinkTable

class CallLinkViewModel : ViewModel() {
  private val isLoadingState: MutableState<Boolean> = mutableStateOf(true)
  val isLoading: State<Boolean> = isLoadingState

  private val callLinkState: MutableState<CallLinkTable.CallLink> = mutableStateOf(
    CallLinkTable.CallLink("", "", AvatarColor.A120, false)
  )
  val callLink: State<CallLinkTable.CallLink> = callLinkState

  fun setName(name: String) {
    callLinkState.value = callLinkState.value.copy(name = name)
  }
}
