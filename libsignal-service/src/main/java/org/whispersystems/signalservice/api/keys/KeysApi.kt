/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.keys

import org.signal.core.util.toByteArray
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.api.push.ServiceIdType
import org.whispersystems.signalservice.internal.push.PushServiceSocket
import java.security.MessageDigest

/**
 * Contains APIs for interacting with /keys endpoints on the service.
 */
class KeysApi(private val pushServiceSocket: PushServiceSocket) {

  companion object {
    @JvmStatic
    fun create(pushServiceSocket: PushServiceSocket): KeysApi {
      return KeysApi(pushServiceSocket)
    }
  }

  /**
   * Checks to see if our local view of our repeated-use prekeys matches the server's view. It's an all-or-nothing match, and no details can be given beyond
   * whether or not everything perfectly matches or not.
   *
   * Status codes:
   * - 200: Everything matches
   * - 409: Something doesn't match
   */
  fun checkRepeatedUseKeys(
    serviceIdType: ServiceIdType,
    identityKey: IdentityKey,
    signedPreKeyId: Int,
    signedPreKey: ECPublicKey,
    lastResortKyberKeyId: Int,
    lastResortKyberKey: KEMPublicKey
  ): NetworkResult<Unit> {
    val digest: MessageDigest = MessageDigest.getInstance("SHA-256").apply {
      update(identityKey.serialize())
      update(signedPreKeyId.toLong().toByteArray())
      update(signedPreKey.serialize())
      update(lastResortKyberKeyId.toLong().toByteArray())
      update(lastResortKyberKey.serialize())
    }

    return NetworkResult.fromFetch {
      pushServiceSocket.checkRepeatedUsePreKeys(serviceIdType, digest.digest())
    }
  }
}
