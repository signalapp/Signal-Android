/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients

import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.phonenumbers.NumberUtil
import org.thoughtcrime.securesms.util.SignalE164Util
import java.io.IOException

/**
 * We operate on recipients many places, but sometimes we find ourselves performing the same recipient-related operations in several locations.
 * This is meant to be a place to put those common operations.
 */
object RecipientRepository {

  private val TAG = Log.tag(RecipientRepository::class.java)

  /**
   * Attempts to lookup a potentially-new recipient by their e164.
   * We will check locally first for a potential match, but may end up hitting the network.
   * This will not create a new recipient if we could not find it in the CDSI directory.
   */
  @WorkerThread
  @JvmStatic
  fun lookupNewE164(inputE164: String): LookupResult {
    val e164 = SignalE164Util.formatAsE164(inputE164)

    if (e164 == null || !NumberUtil.isVisuallyValidNumber(e164)) {
      return LookupResult.InvalidEntry
    }

    val matchingFullRecipientId = SignalDatabase.recipients.getByE164IfRegisteredAndDiscoverable(e164)
    if (matchingFullRecipientId != null) {
      Log.i(TAG, "Already have a full, discoverable recipient for $e164. $matchingFullRecipientId")
      return LookupResult.Success(matchingFullRecipientId)
    }

    Log.i(TAG, "Need to lookup up $e164 with CDSI.")

    return try {
      val result = ContactDiscovery.lookupE164(e164)
      if (result == null) {
        LookupResult.NotFound()
      } else {
        LookupResult.Success(result.recipientId)
      }
    } catch (e: IOException) {
      return LookupResult.NetworkError
    }
  }

  sealed interface LookupResult {
    data class Success(val recipientId: RecipientId) : LookupResult
    object InvalidEntry : LookupResult
    data class NotFound(val recipientId: RecipientId = RecipientId.UNKNOWN) : LookupResult
    object NetworkError : LookupResult
  }
}
