/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.link

import android.Manifest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.permissions.Permissions
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

class UnlinkedSignalContactsViewModel : ViewModel() {

  private val internalState = MutableStateFlow(UnlinkedSignalContactsState())
  val state: StateFlow<UnlinkedSignalContactsState> = internalState.asStateFlow()

  init {
    reload()
  }

  fun reload() {
    viewModelScope.launch {
      val (recipients, hasPermission) = withContext(Dispatchers.IO) {
        val recipients = SignalDatabase.recipients.getSystemContactLinksNeeded().map { Recipient.resolved(it) }
        recipients to hasContactsPermission()
      }

      internalState.update { it.copy(isLoaded = true, recipients = recipients, hasContactsPermission = hasPermission) }
    }
  }

  fun dismiss(recipientId: RecipientId) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) { SignalDatabase.recipients.dismissSystemContactLinkNeeded(recipientId) }
      reload()
    }
  }

  fun dismissAll() {
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        internalState.value.recipients.forEach { SignalDatabase.recipients.dismissSystemContactLinkNeeded(it.id) }
      }
      reload()
    }
  }

  /** With contacts permission newly granted, a contact sync links again whatever it can by number. */
  fun onContactsPermissionGranted() {
    viewModelScope.launch {
      withContext(Dispatchers.IO) { ContactDiscovery.syncRecipientInfoWithSystemContacts(AppDependencies.application) }
      reload()
    }
  }

  private fun hasContactsPermission(): Boolean {
    return Permissions.hasAll(AppDependencies.application, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
  }
}

data class UnlinkedSignalContactsState(
  val isLoaded: Boolean = false,
  val recipients: List<Recipient> = emptyList(),
  val hasContactsPermission: Boolean = true
)
