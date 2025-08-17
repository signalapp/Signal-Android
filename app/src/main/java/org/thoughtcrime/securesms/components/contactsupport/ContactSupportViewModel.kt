/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.contactsupport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogRepository

/**
 * Intended to be used to drive [ContactSupportDialog].
 *
 * @param Reason A type that can be supplied as the reason for showing the dialog when you invoke [showContactSupport]. Useful for when you may want to show
 *   the option for different reasons. Will be given back to you, if set, via [state] in [ContactSupportState.reason].
 */
class ContactSupportViewModel<Reason>(
  val showInitially: Boolean = false
) : ViewModel(), ContactSupportCallbacks {
  private val submitDebugLogRepository: SubmitDebugLogRepository = SubmitDebugLogRepository()

  private val store: MutableStateFlow<ContactSupportState<Reason>> = MutableStateFlow(ContactSupportState(show = showInitially))

  val state: StateFlow<ContactSupportState<Reason>> = store.asStateFlow()

  fun showContactSupport(reason: Reason? = null) {
    store.update { it.copy(show = true, reason = reason) }
  }

  fun hideContactSupport() {
    store.update { ContactSupportState() }
  }

  fun contactSupport(includeLogs: Boolean) {
    viewModelScope.launch {
      if (includeLogs) {
        store.update { it.copy(showAsProgress = true) }
        submitDebugLogRepository.buildAndSubmitLog { result ->
          store.update { ContactSupportState(sendEmail = true, debugLogUrl = result.orNull()) }
        }
      } else {
        store.update { ContactSupportState(sendEmail = true) }
      }
    }
  }

  override fun submitWithDebuglog() {
    contactSupport(true)
  }

  override fun submitWithoutDebuglog() {
    contactSupport(false)
  }

  override fun cancel() {
    hideContactSupport()
  }

  data class ContactSupportState<Reason>(
    val show: Boolean = false,
    val showAsProgress: Boolean = false,
    val sendEmail: Boolean = false,
    val debugLogUrl: String? = null,
    val reason: Reason? = null
  )
}
