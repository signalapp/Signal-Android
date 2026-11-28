/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy.hidden

import kotlinx.coroutines.withContext
import org.signal.appsettings.hiddencontacts.HiddenContactsState
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * All of the storage access behind [HiddenContactsViewModel].
 */
class HiddenContactsRepository {

  suspend fun getHiddenContacts(): List<HiddenContactsState.Contact> {
    return withContext(SignalDispatchers.Default) {
      val context = AppDependencies.application

      SignalDatabase.recipients.getHiddenIds()
        .map { HiddenContactsState.Contact(id = it, name = Recipient.resolved(it).getDisplayName(context)) }
        .sortedBy { it.name.lowercase() }
    }
  }

  /** Unhiding puts the person back in the contact list, which means sharing our profile with them again. */
  suspend fun unhide(id: RecipientId) {
    withContext(SignalDispatchers.Default) {
      SignalDatabase.recipients.setProfileSharing(id, true)
    }
  }
}
