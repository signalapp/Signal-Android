/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients

import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.phonenumbers.NumberUtil
import org.thoughtcrime.securesms.util.SignalE164Util
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * We operate on recipients many places, but sometimes we find ourselves performing the same recipient-related operations in several locations.
 * This is meant to be a place to put those common operations.
 */
object RecipientRepository {

  private val TAG = Log.tag(RecipientRepository::class.java)

  /**
   * Validates whether the provided [PhoneNumber] is a registered Signal user. Checks locally first, then queries the CDSI directory if needed.
   */
  suspend fun lookup(phone: PhoneNumber): PhoneLookupResult {
    return withContext(Dispatchers.IO) {
      lookupNewE164(inputE164 = phone.value)
    }
  }

  /**
   * Validates whether the provided [RecipientId]s are registered Signal users.
   */
  suspend fun lookup(recipientIds: List<RecipientId>): IdLookupResult {
    val recipientsNeedingRegistrationCheck = Recipient
      .resolvedList(recipientIds)
      .filter { !it.isRegistered || !it.hasServiceId }
      .toSet()

    Log.d(TAG, "Need to do ${recipientsNeedingRegistrationCheck.size} registration checks.")

    withContext(Dispatchers.IO) {
      try {
        ContactDiscovery.refresh(
          context = AppDependencies.application,
          recipients = recipientsNeedingRegistrationCheck.toList(),
          notifyOfNewUsers = false,
          timeoutMs = 30.seconds.inWholeMilliseconds
        )
      } catch (e: IOException) {
        Log.w(TAG, "Failed to refresh registered status for ${recipientsNeedingRegistrationCheck.size} recipients", e)
      }
    }

    val allRecipients = Recipient.resolvedList(recipientIds)
    val (registeredRecipients, unregisteredRecipients) = allRecipients.partition { it.isRegistered && it.hasServiceId }
    return if (unregisteredRecipients.isNotEmpty()) {
      Log.w(TAG, "Found ${unregisteredRecipients.size} non-Signal users: ${unregisteredRecipients.joinToString(", ") { it.id.toString() }}")
      IdLookupResult.FoundSome(found = registeredRecipients, notFound = unregisteredRecipients)
    } else {
      IdLookupResult.FoundAll(registeredRecipients)
    }
  }

  /**
   * Attempts to lookup a potentially-new recipient by their e164.
   * We will check locally first for a potential match, but may end up hitting the network.
   * This will not create a new recipient if we could not find it in the CDSI directory.
   */
  @Deprecated("Use [RecipientRepository.lookup(PhoneNumber)] instead.")
  @WorkerThread
  @JvmStatic
  fun lookupNewE164(inputE164: String): PhoneLookupResult {
    val e164 = SignalE164Util.formatAsE164(inputE164)

    if (e164 == null || !NumberUtil.isVisuallyValidNumber(e164)) {
      return PhoneLookupResult.InvalidPhone(inputE164)
    }

    val matchingFullRecipientId = SignalDatabase.recipients.getByE164IfRegisteredAndDiscoverable(e164)
    if (matchingFullRecipientId != null) {
      Log.i(TAG, "Already have a full, discoverable recipient for $e164. $matchingFullRecipientId")
      return PhoneLookupResult.Found(recipient = Recipient.resolved(matchingFullRecipientId), phone = PhoneNumber(e164))
    }

    Log.i(TAG, "Need to lookup up $e164 with CDSI.")

    return try {
      val result = ContactDiscovery.lookupE164(e164)
      if (result == null) {
        PhoneLookupResult.NotFound(PhoneNumber(e164))
      } else {
        PhoneLookupResult.Found(recipient = Recipient.resolved(result.recipientId), phone = PhoneNumber(e164))
      }
    } catch (_: IOException) {
      return LookupResult.NetworkError
    }
  }

  sealed interface LookupResult {
    sealed interface Success : LookupResult
    sealed interface Failure : LookupResult

    data object NetworkError : PhoneLookupResult, IdLookupResult, Failure
  }

  sealed interface PhoneLookupResult : LookupResult {
    sealed interface NoResult : PhoneLookupResult, LookupResult.Failure

    data class Found(val recipient: Recipient, val phone: PhoneNumber) : PhoneLookupResult, LookupResult.Success
    data class NotFound(val phone: PhoneNumber) : NoResult
    data class InvalidPhone(val invalidValue: String) : NoResult
  }

  sealed interface IdLookupResult : LookupResult {
    data class FoundAll(val found: List<Recipient>) : IdLookupResult, LookupResult.Success
    data class FoundSome(val found: List<Recipient>, val notFound: List<Recipient>) : IdLookupResult, LookupResult.Failure
  }
}
