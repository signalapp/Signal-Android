/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.restore

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.registration.proto.RegistrationProvisionMessage
import java.security.InvalidKeyException

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
 * Attempt to parse the PNI identity key pair from the proto message parts.
 */
val RegistrationProvisionMessage.pniIdentityKeyPair: IdentityKeyPair?
  get() {
    return try {
      IdentityKeyPair(
        IdentityKey(pniIdentityKeyPublic.toByteArray()),
        ECPrivateKey(pniIdentityKeyPrivate.toByteArray())
      )
    } catch (_: InvalidKeyException) {
      null
    }
  }
