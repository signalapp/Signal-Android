/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.account

import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.whispersystems.signalservice.api.push.ServiceIdType

/**
 * Represents a bundle of prekeys you want to upload.
 *
 * If a field is nullable, not setting it will simply leave that field alone on the service.
 */
data class PreKeyUpload(
  val serviceIdType: ServiceIdType,
  val signedPreKey: SignedPreKeyRecord?,
  val oneTimeEcPreKeys: List<PreKeyRecord>?,
  val lastResortKyberPreKey: KyberPreKeyRecord?,
  val oneTimeKyberPreKeys: List<KyberPreKeyRecord>?
)
