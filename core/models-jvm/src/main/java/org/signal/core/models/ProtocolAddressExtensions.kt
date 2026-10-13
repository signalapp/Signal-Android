/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:JvmName("ProtocolAddressExtensions")

package org.signal.core.models

import org.signal.libsignal.protocol.SignalProtocolAddress

/**
 * Rebuilds this address from its parsed [ServiceId], so that every string form of the same ServiceId maps to the
 * same address. Addresses whose names are not ServiceIds are returned unchanged.
 */
fun SignalProtocolAddress.normalized(): SignalProtocolAddress {
  return ServiceId.parseOrNull(name, logFailures = false)?.toProtocolAddress(deviceId) ?: this
}
