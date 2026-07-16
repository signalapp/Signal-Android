package org.thoughtcrime.securesms.groups.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MemberSearchViewModel : ViewModel() {
  private val _memberFilter = MutableStateFlow(MemberFilter.ALL)
  val memberFilter: StateFlow<MemberFilter> = _memberFilter

  fun setFilter(filter: MemberFilter) {
    _memberFilter.value = filter
  }

  enum class MemberFilter {
    ALL,
    ADMINS,
    CONTACTS
  }
}
