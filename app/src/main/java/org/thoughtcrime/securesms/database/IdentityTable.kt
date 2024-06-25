/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.database

import android.content.Context
import androidx.core.content.contentValuesOf
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.Base64
import org.signal.core.util.delete
import org.signal.core.util.exists
import org.signal.core.util.firstOrNull
import org.signal.core.util.logging.Log
import org.signal.core.util.requireBoolean
import org.signal.core.util.requireInt
import org.signal.core.util.requireLong
import org.signal.core.util.requireNonNullString
import org.signal.core.util.select
import org.signal.core.util.toOptional
import org.signal.core.util.update
import org.signal.libsignal.protocol.IdentityKey
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.recipients
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.database.model.IdentityStoreRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.IdentityUtil
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.util.UuidUtil
import java.lang.AssertionError
import java.util.Optional

class IdentityTable internal constructor(context: Context?, databaseHelper: SignalDatabase?) : DatabaseTable(context, databaseHelper) {

  companion object {
    private val TAG = Log.tag(IdentityTable::class.java)
    const val TABLE_NAME = "identities"
    private const val ID = "_id"
    const val ADDRESS = "address"
    const val IDENTITY_KEY = "identity_key"
    private const val FIRST_USE = "first_use"
    private const val TIMESTAMP = "timestamp"
    const val VERIFIED = "verified"
    private const val NONBLOCKING_APPROVAL = "nonblocking_approval"
    const val CREATE_TABLE = """
      CREATE TABLE $TABLE_NAME (
        $ID INTEGER PRIMARY KEY AUTOINCREMENT, 
        $ADDRESS INTEGER UNIQUE, 
        $IDENTITY_KEY TEXT, 
        $FIRST_USE INTEGER DEFAULT 0, 
        $TIMESTAMP INTEGER DEFAULT 0, 
        $VERIFIED INTEGER DEFAULT 0, 
        $NONBLOCKING_APPROVAL INTEGER DEFAULT 0
      )
    """
  }

  fun getIdentityStoreRecord(serviceId: ServiceId?): IdentityStoreRecord? {
    return if (serviceId != null) {
      getIdentityStoreRecord(serviceId.toString())
    } else {
      null
    }
  }

  fun getIdentityStoreRecord(addressName: String): IdentityStoreRecord? {
    readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$ADDRESS = ?", addressName)
      .run()
      .use { cursor ->
        if (cursor.moveToFirst()) {
          return IdentityStoreRecord(
            addressName = addressName,
            identityKey = IdentityKey(Base64.decode(cursor.requireNonNullString(IDENTITY_KEY)), 0),
            verifiedStatus = VerifiedStatus.forState(cursor.requireInt(VERIFIED)),
            firstUse = cursor.requireBoolean(FIRST_USE),
            timestamp = cursor.requireLong(TIMESTAMP),
            nonblockingApproval = cursor.requireBoolean(NONBLOCKING_APPROVAL)
          )
        } else if (UuidUtil.isUuid(addressName)) {
          val byServiceId = recipients.getByServiceId(ServiceId.parseOrThrow(addressName))
          if (byServiceId.isPresent) {
            val recipient = Recipient.resolved(byServiceId.get())
            if (recipient.hasE164 && !UuidUtil.isUuid(recipient.requireE164())) {
              Log.i(TAG, "Could not find identity for UUID. Attempting E164.")
              return getIdentityStoreRecord(recipient.requireE164())
            } else {
              Log.i(TAG, "Could not find identity for UUID, and our recipient doesn't have an E164.")
            }
          } else {
            Log.i(TAG, "Could not find identity for UUID, and we don't have a recipient.")
          }
        } else {
          Log.i(TAG, "Could not find identity for E164 either.")
        }
      }

    return null
  }

  fun saveIdentity(
    addressName: String,
    recipientId: RecipientId,
    identityKey: IdentityKey,
    verifiedStatus: VerifiedStatus,
    firstUse: Boolean,
    timestamp: Long,
    nonBlockingApproval: Boolean
  ) {
    saveIdentityInternal(addressName, recipientId, identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval)
    recipients.markNeedsSync(recipientId)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  fun setApproval(addressName: String, recipientId: RecipientId, nonBlockingApproval: Boolean) {
    val updated = writableDatabase
      .update(TABLE_NAME)
      .values(NONBLOCKING_APPROVAL to nonBlockingApproval)
      .where("$ADDRESS = ?", addressName)
      .run()

    if (updated > 0) {
      recipients.markNeedsSync(recipientId)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  fun setVerified(addressName: String, recipientId: RecipientId, identityKey: IdentityKey, verifiedStatus: VerifiedStatus) {
    val updated = writableDatabase
      .update(TABLE_NAME)
      .values(VERIFIED to verifiedStatus.toInt())
      .where("$ADDRESS = ? AND $IDENTITY_KEY = ?", addressName, Base64.encodeWithPadding(identityKey.serialize()))
      .run()

    if (updated > 0) {
      val record = getIdentityRecord(addressName)
      if (record.isPresent) {
        EventBus.getDefault().post(record.get())
      }
      recipients.markNeedsSync(recipientId)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }

  fun updateIdentityAfterSync(addressName: String, recipientId: RecipientId, identityKey: IdentityKey, verifiedStatus: VerifiedStatus) {
    val existingRecord = getIdentityRecord(addressName)
    val hadEntry = existingRecord.isPresent
    val keyMatches = hasMatchingKey(addressName, identityKey)
    val statusMatches = keyMatches && hasMatchingStatus(addressName, identityKey, verifiedStatus)

    if (!keyMatches || !statusMatches) {
      saveIdentityInternal(addressName, recipientId, identityKey, verifiedStatus, !hadEntry, System.currentTimeMillis(), nonBlockingApproval = true)

      val record = getIdentityRecord(addressName)
      if (record.isPresent) {
        EventBus.getDefault().post(record.get())
      }

      AppDependencies.protocolStore.aci().identities().invalidate(addressName)
    }

    if (hadEntry && !keyMatches) {
      Log.w(TAG, "Updated identity key during storage sync for " + addressName + " | Existing: " + existingRecord.get().identityKey.hashCode() + ", New: " + identityKey.hashCode())
      IdentityUtil.markIdentityUpdate(context, recipientId)
    }
  }

  fun delete(addressName: String) {
    writableDatabase
      .delete(TABLE_NAME)
      .where("$ADDRESS = ?", addressName)
      .run()
  }

  private fun getIdentityRecord(addressName: String): Optional<IdentityRecord> {
    return readableDatabase
      .select()
      .from(TABLE_NAME)
      .where("$ADDRESS = ?", addressName)
      .run()
      .firstOrNull { cursor ->
        IdentityRecord(
          recipientId = RecipientId.fromSidOrE164(cursor.requireNonNullString(ADDRESS)),
          identityKey = IdentityKey(Base64.decode(cursor.requireNonNullString(IDENTITY_KEY)), 0),
          verifiedStatus = VerifiedStatus.forState(cursor.requireInt(VERIFIED)),
          firstUse = cursor.requireBoolean(FIRST_USE),
          timestamp = cursor.requireLong(TIMESTAMP),
          nonblockingApproval = cursor.requireBoolean(NONBLOCKING_APPROVAL)
        )
      }
      .toOptional()
  }

  private fun hasMatchingKey(addressName: String, identityKey: IdentityKey): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$ADDRESS = ? AND $IDENTITY_KEY = ?", addressName, Base64.encodeWithPadding(identityKey.serialize()))
      .run()
  }

  private fun hasMatchingStatus(addressName: String, identityKey: IdentityKey, verifiedStatus: VerifiedStatus): Boolean {
    return readableDatabase
      .exists(TABLE_NAME)
      .where("$ADDRESS = ? AND $IDENTITY_KEY = ? AND $VERIFIED = ?", addressName, Base64.encodeWithPadding(identityKey.serialize()), verifiedStatus.toInt())
      .run()
  }

  private fun saveIdentityInternal(
    addressName: String,
    recipientId: RecipientId,
    identityKey: IdentityKey,
    verifiedStatus: VerifiedStatus,
    firstUse: Boolean,
    timestamp: Long,
    nonBlockingApproval: Boolean
  ) {
    val contentValues = contentValuesOf(
      ADDRESS to addressName,
      IDENTITY_KEY to Base64.encodeWithPadding(identityKey.serialize()),
      TIMESTAMP to timestamp,
      VERIFIED to verifiedStatus.toInt(),
      NONBLOCKING_APPROVAL to if (nonBlockingApproval) 1 else 0,
      FIRST_USE to if (firstUse) 1 else 0
    )
    writableDatabase.replace(TABLE_NAME, null, contentValues)
    EventBus.getDefault().post(IdentityRecord(recipientId, identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval))
  }

  enum class VerifiedStatus {
    DEFAULT,
    VERIFIED,
    UNVERIFIED;

    fun toInt(): Int {
      return when (this) {
        DEFAULT -> 0
        VERIFIED -> 1
        UNVERIFIED -> 2
      }
    }

    companion object {
      @JvmStatic
      fun forState(state: Int): VerifiedStatus {
        return when (state) {
          0 -> DEFAULT
          1 -> VERIFIED
          2 -> UNVERIFIED
          else -> throw AssertionError("No such state: $state")
        }
      }
    }
  }
}
