/**
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service.webrtc.links

import android.os.Parcelable
import com.google.protobuf.ByteString
import kotlinx.parcelize.Parcelize
import org.signal.ringrtc.CallLinkRootKey
import org.thoughtcrime.securesms.util.Base64

@Parcelize
class CallLinkRoomId private constructor(private val roomId: ByteArray) : Parcelable {
  fun serialize(): String = Base64.encodeBytes(roomId)

  fun encodeForProto(): ByteString = ByteString.copyFrom(roomId)

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
