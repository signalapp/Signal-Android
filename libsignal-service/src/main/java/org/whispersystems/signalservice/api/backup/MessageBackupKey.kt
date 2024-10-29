/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.backup

import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.signal.libsignal.messagebackup.BackupKey as LibSignalBackupKey
import org.signal.libsignal.messagebackup.MessageBackupKey as LibSignalMessageBackupKey

/**
 * Safe typing around a backup key, which is a 32-byte array.
 * This key is derived from the master key.
 */
class MessageBackupKey(override val value: ByteArray) : BackupKey {
  init {
    require(value.size == 32) { "Backup key must be 32 bytes!" }
  }

  /**
   * The private key used to generate anonymous credentials when interacting with the backup service.
   */
  override fun deriveAnonymousCredentialPrivateKey(aci: ACI): ECPrivateKey {
    return LibSignalBackupKey(value).deriveEcKey(aci.libSignalAci)
  }

  /**
   * The cryptographic material used to encrypt a backup.
   */
  fun deriveBackupSecrets(aci: ACI): BackupKeyMaterial {
    val backupId = deriveBackupId(aci)
    val libsignalBackupKey = LibSignalBackupKey(value)
    val libsignalMessageMessageBackupKey = LibSignalMessageBackupKey(libsignalBackupKey, backupId.value)

    return BackupKeyMaterial(
      id = backupId,
      macKey = libsignalMessageMessageBackupKey.hmacKey,
      aesKey = libsignalMessageMessageBackupKey.aesKey
    )
  }

  /**
   * Identifies a the location of a user's backup.
   */
  fun deriveBackupId(aci: ACI): BackupId {
    return BackupId(
      LibSignalBackupKey(value).deriveBackupId(aci.libSignalAci)
    )
  }

  class BackupKeyMaterial(
    val id: BackupId,
    val macKey: ByteArray,
    val aesKey: ByteArray
  )
}
