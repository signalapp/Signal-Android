/**
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc.links

import android.os.Parcelable
import com.google.protobuf.ByteString
import kotlinx.parcelize.Parcelize
import org.signal.core.util.Serializer
import org.signal.ringrtc.CallLinkRootKey
import org.thoughtcrime.securesms.util.Base64

@Parcelize
class CallLinkRoomId private constructor(private val roomId: ByteArray) : Parcelable {
  fun serialize(): String = DatabaseSerializer.serialize(this)

  fun encodeForProto(): ByteString = ByteString.copyFrom(roomId)

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

  override fun toString(): String {
    return DatabaseSerializer.serialize(this)
  }

  object DatabaseSerializer : Serializer<CallLinkRoomId, String> {
    override fun serialize(data: CallLinkRoomId): String {
      return Base64.encodeBytes(data.roomId)
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
