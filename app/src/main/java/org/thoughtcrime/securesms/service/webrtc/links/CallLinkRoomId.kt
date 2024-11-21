/**
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc.links

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.signal.core.util.Base64
import org.signal.core.util.Hex
import org.signal.core.util.Serializer
import org.signal.ringrtc.CallLinkRootKey

@Parcelize
class CallLinkRoomId private constructor(private val roomId: ByteArray) : Parcelable {
  fun serialize(): String = DatabaseSerializer.serialize(this)

  fun encodeForProto(): ByteString = roomId.toByteString()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CallLinkRoomId

    if (!roomId.contentEquals(other.roomId)) return false

    return true
  }

  override fun hashCode(): Int {
    return roomId.contentHashCode()
  }

  /**
   * Prints call link room id as a hex string, explicitly for logging.
   */
  override fun toString(): String {
    return Hex.toStringCondensed(roomId)
  }

  object DatabaseSerializer : Serializer<CallLinkRoomId, String> {
    override fun serialize(data: CallLinkRoomId): String {
      return Base64.encodeWithPadding(data.roomId)
    }

    override fun deserialize(data: String): CallLinkRoomId {
      return fromBytes(Base64.decode(data))
    }
  }

  companion object {
    @JvmStatic
    fun fromBytes(byteArray: ByteArray): CallLinkRoomId {
      return CallLinkRoomId(byteArray)
    }

    fun fromCallLinkRootKey(callLinkRootKey: CallLinkRootKey): CallLinkRoomId {
      return CallLinkRoomId(callLinkRootKey.deriveRoomId())
    }
  }
}
