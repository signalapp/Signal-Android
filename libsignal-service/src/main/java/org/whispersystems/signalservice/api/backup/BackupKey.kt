/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.backup

import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.whispersystems.signalservice.api.push.ServiceId.ACI

/**
 * Contains the common properties for all "backup keys", namely the [MessageBackupKey] and [MediaRootBackupKey]
 */
interface BackupKey {

  val value: ByteArray

  /**
   * The private key used to generate anonymous credentials when interacting with the backup service.
   */
  fun deriveAnonymousCredentialPrivateKey(aci: ACI): ECPrivateKey
}
