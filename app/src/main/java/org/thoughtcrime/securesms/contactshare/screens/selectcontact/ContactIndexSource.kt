/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.selectcontact

import android.content.Context
import kotlinx.coroutines.withContext
import org.signal.contacts.SystemContactsRepository
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.contacts.index.ContactIndexBuildResult
import org.thoughtcrime.securesms.contacts.index.ContactIndexRecord
import org.thoughtcrime.securesms.contacts.index.ContactIndexRepository
import org.thoughtcrime.securesms.contactshare.SharedContactSource
import org.thoughtcrime.securesms.dependencies.AppDependencies

/** Backs the picker with the real contact index, owning the dispatcher hop so the view model does not. */
class ContactIndexSource(
  private val repository: ContactIndexRepository,
  private val context: Context = AppDependencies.application
) {

  companion object {
    private val TAG = Log.tag(ContactIndexSource::class)
  }

  suspend fun build(): ContactIndexBuildResult = withContext(SignalDispatchers.IO) {
    repository.build()
  }

  /** A window of rows, filtered by [query] when it is not blank. */
  suspend fun page(query: String, startPosition: Long, limit: Int): List<ContactIndexRecord> = withContext(SignalDispatchers.IO) {
    repository.search(query, startPosition, limit)
  }

  /** How many rows the unfiltered index holds, which is what lets a page report its true length. */
  suspend fun count(): Int = withContext(SignalDispatchers.IO) {
    repository.count()
  }

  /**
   * An address book row is handed on as a contact URI so the editor can read the full set of details.
   * The URI is resolved from the lookup key rather than built from the stored contact id, because the
   * provider may have re-aggregated since the index was built.
   */
  suspend fun resolve(record: ContactIndexRecord): SharedContactSource? = withContext(SignalDispatchers.IO) {
    if (record.lookupKey != null && record.contactId != null) {
      val uri = try {
        SystemContactsRepository.currentContactUri(context, record.lookupKey, record.contactId)
      } catch (e: SecurityException) {
        Log.w(TAG, "Contacts permission went away after the index was built.", e)
        null
      }

      if (uri != null) {
        return@withContext SharedContactSource.AddressBook(uri, record.recipientId)
      }

      Log.w(TAG, "No readable address book entry. Falling back to the Signal profile if there is one.")
    }

    record.recipientId?.let { SharedContactSource.SignalContact(it) }
  }

  /** `onCleared()` is on the main thread and nothing reads the index after this, so it is handed off. */
  fun close() {
    SignalExecutors.BOUNDED_IO.execute { repository.close() }
  }
}
