package org.thoughtcrime.securesms.calls.links.create

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.database.CallLinkTable

class CreateCallLinkViewModel : ViewModel() {
  private val _callLink: MutableState<CallLinkTable.CallLink> = mutableStateOf(
    CallLinkTable.CallLink("", "", AvatarColor.random(), false)
  )
  val callLink: State<CallLinkTable.CallLink> = _callLink

  fun setApproveAllMembers(approveAllMembers: Boolean) {
    _callLink.value = _callLink.value.copy(isApprovalRequired = approveAllMembers)
  }

  fun toggleApproveAllMembers() {
    _callLink.value = _callLink.value.copy(isApprovalRequired = _callLink.value.isApprovalRequired)
  }

  fun setCallName(callName: String) {
    _callLink.value = _callLink.value.copy(name = callName)
  }
}
