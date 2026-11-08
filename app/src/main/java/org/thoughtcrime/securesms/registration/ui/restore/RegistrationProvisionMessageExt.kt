/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.registration.proto.RegistrationProvisionMessage
import java.security.InvalidKeyException

/**
 * The phone number, or null if absent or empty.
 */
val RegistrationProvisionMessage.e164OrNull: String?
  get() = e164?.takeIf { it.isNotEmpty() }

/**
 * Attempt to parse the ACI identity key pair from the proto message parts.
 */
val RegistrationProvisionMessage.aciIdentityKeyPair: IdentityKeyPair?
  get() {
    return try {
      IdentityKeyPair(
        IdentityKey(aciIdentityKeyPublic.toByteArray()),
        ECPrivateKey(aciIdentityKeyPrivate.toByteArray())
      )
    } catch (_: InvalidKeyException) {
      null
    }
  }

/**
 * Attempt to parse the PNI identity key pair from the proto message parts. Null if absent or empty.
 */
val RegistrationProvisionMessage.pniIdentityKeyPair: IdentityKeyPair?
  get() {
    val publicKey = pniIdentityKeyPublic?.takeIf { it.size > 0 } ?: return null
    val privateKey = pniIdentityKeyPrivate?.takeIf { it.size > 0 } ?: return null

    return try {
      IdentityKeyPair(
        IdentityKey(publicKey.toByteArray()),
        ECPrivateKey(privateKey.toByteArray())
      )
    } catch (_: InvalidKeyException) {
      null
    }
  }
