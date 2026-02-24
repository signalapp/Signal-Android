/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.util

import android.os.Parcel
import kotlinx.parcelize.Parceler
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey

class IdentityKeyPairParceler : Parceler<IdentityKeyPair> {
  override fun IdentityKeyPair.write(parcel: Parcel, flags: Int) {
    parcel.writeByteArray(publicKey.serialize())
    parcel.writeByteArray(privateKey.serialize())
  }

  override fun create(parcel: Parcel): IdentityKeyPair {
    return IdentityKeyPair(
      IdentityKey(parcel.createByteArray()!!),
      ECPrivateKey(parcel.createByteArray()!!)
    )
  }
}
