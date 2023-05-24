/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.svr

import org.signal.libsignal.svr2.PinHash
import org.signal.libsignal.svr2.Svr2Client
import org.whispersystems.signalservice.internal.push.AuthCredentials

/**
 * Encapsulates the various dependencies needed to create a [PinHash] in SVR2 without having to expose them.
 */
internal class Svr2PinHasher(
  private val authCredentials: AuthCredentials,
  private val client: Svr2Client
) {
  fun hash(normalizedPin: ByteArray): PinHash {
    return client.hashPin(normalizedPin, authCredentials.username().toByteArray(Charsets.UTF_8))
  }
}
