/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients

import android.annotation.SuppressLint
import android.os.Parcelable
import androidx.annotation.AnyThread
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.signal.core.util.DatabaseId
import org.signal.core.util.LongSerializer
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.groups.GroupId
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.SignalServiceAddress
import java.util.regex.Pattern

@Parcelize
@Serializable
class RecipientId private constructor(private val id: Long) : Parcelable, Comparable<RecipientId>, DatabaseId {

  companion object {

    private val TAG = Log.tag(RecipientId::class)
    private const val UNKNOWN_ID = -1L
    private const val DELIMITER = ','

    @JvmField
    val UNKNOWN = from(UNKNOWN_ID)

    @JvmField
    val SERIALIZER: LongSerializer<RecipientId> = Serializer()

    @JvmStatic
    fun from(id: Long): RecipientId {
      if (id == 0L) {
        throw InvalidLongRecipientIdError()
      }

      return RecipientId(id)
    }

    @JvmStatic
    fun from(id: String): RecipientId {
      try {
        return from(id.toLong())
      } catch (_: NumberFormatException) {
        throw InvalidStringRecipientIdError()
      }
    }

    @JvmStatic
    fun fromNullable(id: String?): RecipientId? = id?.let { from(it) }

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
        return UNKNOWN
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

    @JvmStatic
    fun toSerializedList(ids: Collection<RecipientId>): String {
      return ids.joinToString(DELIMITER.toString()) { it.serialize() }
    }

    @JvmStatic
    fun fromSerializedList(serialized: String): List<RecipientId> {
      return serialized.split(DELIMITER).filter { it.isNotEmpty() }.map { from(it) }
    }

    @JvmStatic
    fun serializedListContains(serialized: String, recipientId: RecipientId): Boolean {
      return Pattern.compile("\\b${recipientId.serialize()}\\b")
        .matcher(serialized)
        .find()
    }
  }

  override fun serialize(): String = id.toString()

  override fun toString(): String = "RecipientId::$id"

  fun toLong(): Long = id

  @Transient
  @IgnoredOnParcel
  val isUnknown: Boolean = id == UNKNOWN_ID

  @JvmOverloads
  fun toQueueKey(forMedia: Boolean = false): String {
    return "RecipientId::$id${if (forMedia) "::MEDIA" else ""}"
  }

  fun toScheduledSendQueueKey(): String = "RecipientId::$id::SCHEDULED"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as RecipientId

    return id == other.id
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }

  override fun compareTo(other: RecipientId): Int {
    return id.compareTo(other.id)
  }

  private class InvalidLongRecipientIdError : AssertionError()
  private class InvalidStringRecipientIdError : AssertionError()

  private class Serializer : LongSerializer<RecipientId> {
    override fun serialize(data: RecipientId): Long = data.toLong()
    override fun deserialize(input: Long): RecipientId = from(input)
  }
}
