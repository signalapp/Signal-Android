/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.models.backup

import org.signal.core.models.ServiceId
import org.signal.libsignal.messagebackup.BackupForwardSecrecyToken
import org.signal.libsignal.messagebackup.MessageBackupKey
import org.signal.libsignal.protocol.ecc.ECPrivateKey

private typealias LibSignalBackupKey = org.signal.libsignal.messagebackup.BackupKey

/**
 * Safe typing around a backup key, which is a 32-byte array.
 * This key is derived from the AEP.
 */
class MessageBackupKey(override val value: ByteArray) : BackupKey {
  init {
    require(value.size == 32) { "Backup key must be 32 bytes!" }
  }

  /**
   * The private key used to generate anonymous credentials when interacting with the backup service.
   */
  override fun deriveAnonymousCredentialPrivateKey(aci: ServiceId.ACI): ECPrivateKey {
    return LibSignalBackupKey(value).deriveEcKey(aci.libSignalAci)
  }

  /**
   * The cryptographic material used to encrypt a backup.
   *
   * @param forwardSecrecyToken Should be present for any backup located on the archive CDN. Absent for other uses (i.e. link+sync).
   */
  fun deriveBackupSecrets(aci: ServiceId.ACI, forwardSecrecyToken: BackupForwardSecrecyToken?): BackupKeyMaterial {
    val backupId = deriveBackupId(aci)
    val libsignalBackupKey = LibSignalBackupKey(value)
    val libsignalMessageMessageBackupKey = MessageBackupKey(libsignalBackupKey, backupId.value, forwardSecrecyToken)

    return BackupKeyMaterial(
      id = backupId,
      macKey = libsignalMessageMessageBackupKey.hmacKey,
      aesKey = libsignalMessageMessageBackupKey.aesKey
    )
  }

  /**
   * Identifies a the location of a user's backup.
   */
  fun deriveBackupId(aci: ServiceId.ACI): BackupId {
    return BackupId(
      LibSignalBackupKey(value).deriveBackupId(aci.libSignalAci)
    )
  }

  /**
   * The AES key used to encrypt the backup id for local file backup metadata header.
   */
  fun deriveLocalBackupMetadataKey(): ByteArray {
    return LibSignalBackupKey(value).deriveLocalBackupMetadataKey()
  }

  class BackupKeyMaterial(
    val id: BackupId,
    val macKey: ByteArray,
    val aesKey: ByteArray
  )
}
