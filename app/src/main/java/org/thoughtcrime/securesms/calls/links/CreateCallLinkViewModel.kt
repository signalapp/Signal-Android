package org.thoughtcrime.securesms.calls.links

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class CreateCallLinkViewModel : ViewModel() {
  private val _callName: MutableState<String> = mutableStateOf("")
  private val _callLink: MutableState<String> = mutableStateOf("")
  private val _approveAllMembers: MutableState<Boolean> = mutableStateOf(false)

  val callName: State<String> = _callName
  val callLink: State<String> = _callLink
  val approveAllMembers: State<Boolean> = _approveAllMembers

  fun setApproveAllMembers(approveAllMembers: Boolean) {
    _approveAllMembers.value = approveAllMembers
  }

  fun toggleApproveAllMembers() {
    _approveAllMembers.value = !_approveAllMembers.value
  }

  fun setCallName(callName: String) {
    _callName.value = callName
  }

  fun setCallLink(callLink: String) {
    _callLink.value = callLink
  }
}
