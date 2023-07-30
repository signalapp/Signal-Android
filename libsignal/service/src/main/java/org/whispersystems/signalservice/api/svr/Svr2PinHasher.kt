/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.svr

import org.signal.libsignal.svr2.PinHash
import org.whispersystems.signalservice.internal.push.AuthCredentials

/**
 * Encapsulates the various dependencies needed to create a [PinHash] in SVR2 without having to expose them.
 */
internal class Svr2PinHasher(
  private val authCredentials: AuthCredentials,
  private val mrEnclave: ByteArray
) {
  fun hash(normalizedPin: ByteArray): PinHash {
    return PinHash.svr2(normalizedPin, authCredentials.username(), mrEnclave)
  }
}
