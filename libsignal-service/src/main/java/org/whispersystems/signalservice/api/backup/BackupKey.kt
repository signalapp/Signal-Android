/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.backup

import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.ecc.ECPrivateKey
import org.signal.libsignal.protocol.kdf.HKDF
import org.whispersystems.signalservice.api.push.ServiceId.ACI

/**
 * Safe typing around a backup key, which is a 32-byte array.
 */
class BackupKey(val value: ByteArray) {
  init {
    require(value.size == 32) { "Backup key must be 32 bytes!" }
  }

  /**
   * Identifies a the location of a user's backup.
   */
  fun deriveBackupId(aci: ACI): BackupId {
    return BackupId(
      HKDF.deriveSecrets(this.value, aci.toByteArray(), "20231003_Signal_Backups_GenerateBackupId".toByteArray(), 16)
    )
  }

  /**
   * The cryptographic material used to encrypt a backup.
   */
  fun deriveBackupSecrets(aci: ACI): BackupKeyMaterial {
    val backupId = deriveBackupId(aci)

    val extendedKey = HKDF.deriveSecrets(this.value, backupId.value, "20231003_Signal_Backups_EncryptMessageBackup".toByteArray(), 80)

    return BackupKeyMaterial(
      id = backupId,
      macKey = extendedKey.copyOfRange(0, 32),
      cipherKey = extendedKey.copyOfRange(32, 64)
    )
  }

  /**
   * The private key used to generate anonymous credentials when interacting with the backup service.
   */
  fun deriveAnonymousCredentialPrivateKey(aci: ACI): ECPrivateKey {
    val material = HKDF.deriveSecrets(this.value, aci.toByteArray(), "20231003_Signal_Backups_GenerateBackupIdKeyPair".toByteArray(), 32)
    return Curve.decodePrivatePoint(material)
  }

  fun deriveMediaId(mediaName: MediaName): MediaId {
    return MediaId(HKDF.deriveSecrets(value, mediaName.toByteArray(), "20231003_Signal_Backups_Media_ID".toByteArray(), 15))
  }

  fun deriveMediaSecrets(mediaName: MediaName): MediaKeyMaterial {
    return deriveMediaSecrets(deriveMediaId(mediaName))
  }

  fun deriveMediaSecretsFromMediaId(base64MediaId: String): MediaKeyMaterial {
    return deriveMediaSecrets(MediaId(base64MediaId))
  }

  fun deriveThumbnailTransitKey(thumbnailMediaName: MediaName): ByteArray {
    return HKDF.deriveSecrets(value, deriveMediaId(thumbnailMediaName).value, "20240513_Signal_Backups_EncryptThumbnail".toByteArray(), 64)
  }

  private fun deriveMediaSecrets(mediaId: MediaId): MediaKeyMaterial {
    val extendedKey = HKDF.deriveSecrets(this.value, mediaId.value, "20231003_Signal_Backups_EncryptMedia".toByteArray(), 80)

    return MediaKeyMaterial(
      id = mediaId,
      macKey = extendedKey.copyOfRange(0, 32),
      cipherKey = extendedKey.copyOfRange(32, 64),
      iv = extendedKey.copyOfRange(64, 80)
    )
  }

  class BackupKeyMaterial(
    val id: BackupId,
    val macKey: ByteArray,
    val cipherKey: ByteArray
  )

  class MediaKeyMaterial(
    val id: MediaId,
    val macKey: ByteArray,
    val cipherKey: ByteArray,
    val iv: ByteArray
  ) {
    companion object {
      @JvmStatic
      fun forMedia(id: ByteArray, keyMac: ByteArray, iv: ByteArray): MediaKeyMaterial {
        return MediaKeyMaterial(
          MediaId(id),
          keyMac.copyOfRange(32, 64),
          keyMac.copyOfRange(0, 32),
          iv
        )
      }
    }
  }
}
