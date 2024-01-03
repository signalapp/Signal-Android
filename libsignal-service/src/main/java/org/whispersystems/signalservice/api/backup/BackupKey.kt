/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.backup

import org.signal.libsignal.protocol.kdf.HKDF
import org.whispersystems.signalservice.api.push.ServiceId.ACI

/**
 * Safe typing around a backup key, which is a 32-byte array.
 */
class BackupKey(val value: ByteArray) {
  init {
    require(value.size == 32) { "Backup key must be 32 bytes!" }
  }

  fun deriveSecrets(aci: ACI): KeyMaterial {
    val backupId = BackupId(
      HKDF.deriveSecrets(this.value, aci.toByteArray(), "20231003_Signal_Backups_GenerateBackupId".toByteArray(), 16)
    )

    val extendedKey = HKDF.deriveSecrets(this.value, backupId.value, "20231003_Signal_Backups_EncryptMessageBackup".toByteArray(), 80)

    return KeyMaterial(
      backupId = backupId,
      macKey = extendedKey.copyOfRange(0, 32),
      cipherKey = extendedKey.copyOfRange(32, 64),
      iv = extendedKey.copyOfRange(64, 80)
    )
  }

  class KeyMaterial(
    val backupId: BackupId,
    val macKey: ByteArray,
    val cipherKey: ByteArray,
    val iv: ByteArray
  )
}
