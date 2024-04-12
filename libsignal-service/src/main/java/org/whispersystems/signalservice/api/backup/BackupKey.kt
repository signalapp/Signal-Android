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

  fun deriveBackupId(aci: ACI): BackupId {
    return BackupId(
      HKDF.deriveSecrets(this.value, aci.toByteArray(), "20231003_Signal_Backups_GenerateBackupId".toByteArray(), 16)
    )
  }

  fun deriveSecrets(aci: ACI): KeyMaterial<BackupId> {
    val backupId = deriveBackupId(aci)

    val extendedKey = HKDF.deriveSecrets(this.value, backupId.value, "20231003_Signal_Backups_EncryptMessageBackup".toByteArray(), 80)

    return KeyMaterial(
      id = backupId,
      macKey = extendedKey.copyOfRange(0, 32),
      cipherKey = extendedKey.copyOfRange(32, 64),
      iv = extendedKey.copyOfRange(64, 80)
    )
  }

  fun deriveMediaId(mediaName: MediaName): MediaId {
    return MediaId(HKDF.deriveSecrets(value, mediaName.toByteArray(), "Media ID".toByteArray(), 15))
  }

  fun deriveMediaSecrets(mediaName: MediaName): KeyMaterial<MediaId> {
    return deriveMediaSecrets(deriveMediaId(mediaName))
  }

  fun deriveMediaSecrets(mediaId: MediaId): KeyMaterial<MediaId> {
    val extendedKey = HKDF.deriveSecrets(this.value, mediaId.value, "20231003_Signal_Backups_EncryptMedia".toByteArray(), 80)

    return KeyMaterial(
      id = mediaId,
      macKey = extendedKey.copyOfRange(0, 32),
      cipherKey = extendedKey.copyOfRange(32, 64),
      iv = extendedKey.copyOfRange(64, 80)
    )
  }

  class KeyMaterial<Id> (
    val id: Id,
    val macKey: ByteArray,
    val cipherKey: ByteArray,
    val iv: ByteArray
  ) {
    companion object {
      @JvmStatic
      fun forMedia(id: ByteArray, keyMac: ByteArray, iv: ByteArray): KeyMaterial<MediaId> {
        return KeyMaterial(
          MediaId(id),
          keyMac.copyOfRange(32, 64),
          keyMac.copyOfRange(0, 32),
          iv
        )
      }
    }
  }
}
