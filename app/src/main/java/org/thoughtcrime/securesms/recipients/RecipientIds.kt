/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients

import android.annotation.SuppressLint
import androidx.annotation.AnyThread
import org.signal.core.models.ServiceId
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.groups.GroupId
import org.whispersystems.signalservice.api.push.SignalServiceAddress

/**
 * Resolves [RecipientId]s from identifiers that require a cache or database lookup. Unlike [RecipientId.from],
 * these may insert or merge recipients as a side effect.
 */
object RecipientIds {

  private val TAG = Log.tag(RecipientIds::class)

  @JvmStatic
  fun from(address: SignalServiceAddress): RecipientId = from(address.serviceId, address.number.orNull())

  @JvmStatic
  fun from(serviceId: ServiceId): RecipientId = from(serviceId, null)

  @JvmStatic
  fun fromE164(identifier: String): RecipientId = from(null, identifier)

  @JvmStatic
  fun from(groupId: GroupId): RecipientId {
    var recipientId = RecipientIdCache.INSTANCE.get(groupId)
    if (recipientId == null) {
      Log.d(TAG, "RecipientId cache miss for $groupId")
      recipientId = SignalDatabase.recipients.getOrInsertFromPossiblyMigratedGroupId(groupId)
      if (groupId.isV2) {
        RecipientIdCache.INSTANCE.put(groupId, recipientId)
      }
    }

    return recipientId
  }

  /**
   * Used for when you have a string that could be either a UUID or an e164. This was primarily
   * created for interacting with protocol stores.
   * @param identifier A UUID or e164
   */
  @JvmStatic
  @AnyThread
  fun fromSidOrE164(identifier: String): RecipientId {
    val serviceId = ServiceId.parseOrNull(identifier)
    return if (serviceId != null) {
      from(serviceId)
    } else {
      from(null, identifier)
    }
  }

  @JvmStatic
  @AnyThread
  @SuppressLint("WrongThread")
  fun from(serviceId: ServiceId?, e164: String?): RecipientId {
    if (serviceId != null && serviceId.isUnknown) {
      return RecipientId.UNKNOWN
    }

    var recipientId = RecipientIdCache.INSTANCE.get(serviceId, e164)
    if (recipientId == null) {
      recipientId = SignalDatabase.recipients.getAndPossiblyMerge(serviceId, e164)
      RecipientIdCache.INSTANCE.put(recipientId, e164, serviceId)
    }

    return recipientId
  }

  @JvmStatic
  @AnyThread
  fun clearCache() {
    RecipientIdCache.INSTANCE.clear()
  }
}
