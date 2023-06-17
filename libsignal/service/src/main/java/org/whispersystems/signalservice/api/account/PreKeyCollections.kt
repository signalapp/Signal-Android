/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.account

import org.signal.libsignal.protocol.IdentityKeyPair

/**
 * A holder class to hold onto two [PreKeyCollection] that are very similar but share an [IdentityKeyPair]
 */
data class PreKeyCollections(
  val identityKeyPair: IdentityKeyPair,
  val aciPreKeyCollection: PreKeyCollection,
  val pniPreKeyCollection: PreKeyCollection
)
