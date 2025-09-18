/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import okio.ByteString
import org.signal.core.util.DatabaseId
import org.signal.core.util.Hex
import org.signal.libsignal.protocol.kdf.HKDF
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.groups.GroupIdentifier
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.libsignal.zkgroup.groups.GroupSecretParams
import org.thoughtcrime.securesms.util.LRUCache
import org.thoughtcrime.securesms.util.Util
import java.io.IOException
import java.security.SecureRandom

/**
 * GroupId represents the identifier for a given group.
 *
 * We suppress CanBe Parameter because Parcelize wants the parameters to exist for reconstruction.
 */
@Suppress("CanBeParameter")
@Parcelize
@Serializable
sealed class GroupId(private val encodedId: String) : DatabaseId, Parcelable {

  companion object {
    private const val ENCODED_SIGNAL_GROUP_V1_PREFIX = "__textsecure_group__!"
    private const val ENCODED_SIGNAL_GROUP_V2_PREFIX = "__signal_group__v2__!"
    private const val ENCODED_MMS_GROUP_PREFIX = "__signal_mms_group__!"
    private const val MMS_BYTE_LENGTH = 16
    private const val V1_MMS_BYTE_LENGTH = 16
    private const val V1_BYTE_LENGTH = 16
    private const val V2_BYTE_LENGTH = GroupIdentifier.SIZE

    private val groupIdentifierCache: LRUCache<GroupMasterKey, GroupIdentifier> = LRUCache(1000)

    @JvmStatic
    fun mms(mmsGroupIdBytes: ByteArray): Mms = Mms(mmsGroupIdBytes)

    @JvmStatic
    fun v1orThrow(gv1GroupIdBytes: ByteArray): V1 {
      try {
        return v1(gv1GroupIdBytes)
      } catch (e: BadGroupIdException) {
        throw AssertionError(e)
      }
    }

    @JvmStatic
    @Throws(BadGroupIdException::class)
    fun v1(gv1GroupIdBytes: ByteArray): V1 {
      if (gv1GroupIdBytes.size != V1_BYTE_LENGTH) {
        throw BadGroupIdException()
      }

      return V1(gv1GroupIdBytes)
    }

    @JvmStatic
    fun createV1(secureRandom: SecureRandom): V1 = v1orThrow(Util.getSecretBytes(secureRandom, V1_MMS_BYTE_LENGTH))

    @JvmStatic
    fun createMms(secureRandom: SecureRandom): Mms = mms(Util.getSecretBytes(secureRandom, MMS_BYTE_LENGTH))

    @Throws(BadGroupIdException::class)
    private fun v2(bytes: ByteArray): V2 {
      if (bytes.size != V2_BYTE_LENGTH) {
        throw BadGroupIdException()
      }

      return V2(bytes)
    }

    @JvmStatic
    fun v2(groupIdentifier: GroupIdentifier): V2 {
      try {
        return v2(groupIdentifier.serialize())
      } catch (e: BadGroupIdException) {
        throw AssertionError(e)
      }
    }

    @JvmStatic
    fun v2(masterKey: GroupMasterKey): V2 = v2(getIdentifierForMasterKey(masterKey))

    @JvmStatic
    fun getIdentifierForMasterKey(masterKey: GroupMasterKey): GroupIdentifier {
      var cachedIdentifier: GroupIdentifier? = null
      synchronized(groupIdentifierCache) {
        cachedIdentifier = groupIdentifierCache.get(masterKey)
      }

      if (cachedIdentifier == null) {
        cachedIdentifier = GroupSecretParams.deriveFromMasterKey(masterKey)
          .publicParams
          .groupIdentifier

        synchronized(groupIdentifierCache) {
          groupIdentifierCache.put(masterKey, cachedIdentifier)
        }
      }

      return cachedIdentifier
    }

    @JvmStatic
    @Throws(BadGroupIdException::class)
    fun push(bytes: ByteString): Push {
      return push(bytes.toByteArray())
    }

    @JvmStatic
    @Throws(BadGroupIdException::class)
    fun push(bytes: ByteArray): Push {
      return if (bytes.size == V2_BYTE_LENGTH) v2(bytes) else v1(bytes)
    }

    @JvmStatic
    fun pushOrThrow(bytes: ByteArray): Push {
      try {
        return push(bytes)
      } catch (e: BadGroupIdException) {
        throw AssertionError(e)
      }
    }

    @JvmStatic
    fun pushOrNull(bytes: ByteArray): Push? {
      return try {
        push(bytes)
      } catch (e: BadGroupIdException) {
        null
      }
    }

    @JvmStatic
    fun parseOrThrow(encodedGroupId: String): GroupId {
      try {
        return parse(encodedGroupId)
      } catch (e: BadGroupIdException) {
        throw AssertionError(e)
      }
    }

    @JvmStatic
    @Throws(BadGroupIdException::class)
    fun parse(encodedGroupId: String): GroupId {
      try {
        if (!isEncodedGroup(encodedGroupId)) {
          throw BadGroupIdException("Invalid encoding")
        }

        val bytes = extractDecodedId(encodedGroupId)

        when {
          encodedGroupId.startsWith(ENCODED_SIGNAL_GROUP_V2_PREFIX) -> return v2(bytes)
          encodedGroupId.startsWith(ENCODED_SIGNAL_GROUP_V1_PREFIX) -> return v1(bytes)
          encodedGroupId.startsWith(ENCODED_MMS_GROUP_PREFIX) -> return mms(bytes)
        }

        throw BadGroupIdException()
      } catch (e: IOException) {
        throw BadGroupIdException(e)
      }
    }

    @JvmStatic
    @Throws(BadGroupIdException::class)
    fun parseNullable(encodedGroupId: String?): GroupId? {
      if (encodedGroupId == null) {
        return null
      }

      return parse(encodedGroupId)
    }

    @JvmStatic
    fun parseNullableOrThrow(encodedGroupId: String?): GroupId? {
      if (encodedGroupId == null) {
        return null
      }

      return parseOrThrow(encodedGroupId)
    }

    @JvmStatic
    fun isEncodedGroup(groupId: String): Boolean {
      return groupId.startsWith(ENCODED_SIGNAL_GROUP_V2_PREFIX) ||
        groupId.startsWith(ENCODED_SIGNAL_GROUP_V1_PREFIX) ||
        groupId.startsWith(ENCODED_MMS_GROUP_PREFIX)
    }

    @Throws(IOException::class)
    private fun extractDecodedId(encodedGroupId: String): ByteArray {
      return Hex.fromStringCondensed(encodedGroupId.split("!".toPattern(), 2)[1])
    }
  }

  constructor(prefix: String, bytes: ByteArray) : this(prefix + Hex.toStringCondensed(bytes))

  val decodedId: ByteArray get() {
    try {
      return extractDecodedId(encodedId)
    } catch (e: IOException) {
      throw AssertionError(e)
    }
  }

  override fun toString(): String {
    return encodedId
  }

  override fun serialize(): String {
    return encodedId
  }

  abstract val isMms: Boolean
  abstract val isV1: Boolean
  abstract val isV2: Boolean
  abstract val isPush: Boolean

  fun requireMms(): Mms {
    assert(this is Mms)
    return this as Mms
  }

  fun requireV1(): V1 {
    assert(this is V1)
    return this as V1
  }

  fun requireV2(): V2 {
    assert(this is V2)
    return this as V2
  }

  fun requirePush(): Push {
    assert(this is Push)
    return this as Push
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GroupId

    return encodedId == other.encodedId
  }

  override fun hashCode(): Int {
    return encodedId.hashCode()
  }

  @Serializable
  class Mms(private val mmsBytes: ByteArray) : GroupId(ENCODED_MMS_GROUP_PREFIX, mmsBytes) {
    @Transient
    @IgnoredOnParcel
    override val isMms: Boolean = true

    @Transient
    @IgnoredOnParcel
    override val isV1: Boolean = false

    @Transient
    @IgnoredOnParcel
    override val isV2: Boolean = false

    @Transient
    @IgnoredOnParcel
    override val isPush: Boolean = false
  }

  @Serializable
  sealed class Push(private val prefix: String, open val pushBytes: ByteArray) : GroupId(prefix, pushBytes) {
    @Transient
    @IgnoredOnParcel
    override val isMms: Boolean = false

    @Transient
    @IgnoredOnParcel
    override val isPush: Boolean = true

    @Transient
    @IgnoredOnParcel
    override val isV1: Boolean = false

    @Transient
    @IgnoredOnParcel
    override val isV2: Boolean = false
  }

  @Parcelize
  @Serializable
  class V1(private val v1Bytes: ByteArray) : Push(ENCODED_SIGNAL_GROUP_V1_PREFIX, v1Bytes) {
    @Transient
    @IgnoredOnParcel
    override val isV1: Boolean = true

    fun deriveV2MigrationMasterKey(): GroupMasterKey {
      try {
        return GroupMasterKey(HKDF.deriveSecrets(decodedId, "GV2 Migration".toByteArray(), GroupMasterKey.SIZE))
      } catch (e: InvalidInputException) {
        throw AssertionError(e)
      }
    }

    fun deriveV2MigrationGroupId(): V2 {
      return v2(deriveV2MigrationMasterKey())
    }
  }

  @Parcelize
  @Serializable
  class V2(private val v2Bytes: ByteArray) : Push(ENCODED_SIGNAL_GROUP_V2_PREFIX, v2Bytes) {
    @Transient
    @IgnoredOnParcel
    override val isV2: Boolean = true
  }
}
