/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.archive.stream

import org.signal.archive.proto.BackupInfo
import org.signal.archive.proto.Frame
import org.signal.archive.stream.EncryptedBackupReader.Companion.createForSignalBackup
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.backup.BackupId
import org.signal.core.models.backup.MessageBackupKey
import org.signal.core.util.Util
import org.signal.core.util.stream.MacOutputStream
import org.signal.core.util.writeVarInt32
import org.signal.libsignal.messagebackup.BackupForwardSecrecyToken
import java.io.IOException
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Provides the ability to write backup frames in a streaming fashion to a target [OutputStream].
 * As it's being written, it will be both encrypted and compressed. Specifically, the backup frames
 * are gzipped, that gzipped data is encrypted, and then an HMAC of the encrypted data is appended
 * to the end of the [outputStream].
 */
class EncryptedBackupWriter private constructor(
  keyMaterial: MessageBackupKey.BackupKeyMaterial,
  forwardSecrecyToken: BackupForwardSecrecyToken?,
  forwardSecrecyMetadata: ByteArray?,
  private val outputStream: OutputStream,
  private val append: (ByteArray) -> Unit
) : BackupExportWriter {

  private val mainStream: PaddedGzipOutputStream
  private val macStream: MacOutputStream

  companion object {
    val MAGIC_NUMBER = "SBACKUP".toByteArray(Charsets.UTF_8) + 0x01

    /**
     * Create a writer for a backup from the archive CDN.
     * The key difference is that we require forward secrecy data.
     */
    fun createForSignalBackup(
      key: MessageBackupKey,
      aci: ACI,
      forwardSecrecyToken: BackupForwardSecrecyToken,
      forwardSecrecyMetadata: ByteArray,
      outputStream: OutputStream,
      append: (ByteArray) -> Unit
    ): EncryptedBackupWriter {
      return createForSignalBackup(key, key.deriveBackupId(aci), forwardSecrecyToken, forwardSecrecyMetadata, outputStream, append)
    }

    /**
     * Create a writer for a backup from the archive CDN, using a [BackupId] directly
     * instead of deriving it from an ACI.
     */
    fun createForSignalBackup(
      key: MessageBackupKey,
      backupId: BackupId,
      forwardSecrecyToken: BackupForwardSecrecyToken,
      forwardSecrecyMetadata: ByteArray,
      outputStream: OutputStream,
      append: (ByteArray) -> Unit
    ): EncryptedBackupWriter {
      return EncryptedBackupWriter(
        keyMaterial = key.deriveBackupSecrets(backupId, forwardSecrecyToken),
        forwardSecrecyToken = forwardSecrecyToken,
        forwardSecrecyMetadata = forwardSecrecyMetadata,
        outputStream = outputStream,
        append = append
      )
    }

    /**
     * Create a writer for a local backup or for a transfer to a linked device. Basically everything that isn't [createForSignalBackup].
     * The key difference is that we don't require forward secrecy data.
     */
    fun createForLocalOrLinking(
      key: MessageBackupKey,
      aci: ACI,
      outputStream: OutputStream,
      append: (ByteArray) -> Unit
    ): EncryptedBackupWriter {
      return createForLocalOrLinking(key, key.deriveBackupId(aci), outputStream, append)
    }

    /**
     * Create a writer for a local backup or for a transfer to a linked device, using a [BackupId] directly
     * instead of deriving it from an ACI.
     */
    fun createForLocalOrLinking(
      key: MessageBackupKey,
      backupId: BackupId,
      outputStream: OutputStream,
      append: (ByteArray) -> Unit
    ): EncryptedBackupWriter {
      return EncryptedBackupWriter(
        keyMaterial = key.deriveBackupSecrets(backupId, forwardSecrecyToken = null),
        forwardSecrecyToken = null,
        forwardSecrecyMetadata = null,
        outputStream = outputStream,
        append = append
      )
    }
  }

  init {
    check(
      (forwardSecrecyToken != null && forwardSecrecyMetadata != null) ||
        (forwardSecrecyToken == null && forwardSecrecyMetadata == null)
    )

    if (forwardSecrecyMetadata != null) {
      outputStream.write(MAGIC_NUMBER)
      outputStream.writeVarInt32(forwardSecrecyMetadata.size)
      outputStream.write(forwardSecrecyMetadata)
      outputStream.flush()
    }

    val iv: ByteArray = Util.getSecretBytes(16)
    outputStream.write(iv)
    outputStream.flush()

    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
      init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyMaterial.aesKey, "AES"), IvParameterSpec(iv))
    }

    val mac = Mac.getInstance("HmacSHA256").apply {
      init(SecretKeySpec(keyMaterial.macKey, "HmacSHA256"))
      update(iv)
    }

    macStream = MacOutputStream(outputStream, mac)
    val cipherStream = CipherOutputStream(macStream, cipher)

    mainStream = PaddedGzipOutputStream(cipherStream)
  }

  override fun write(header: BackupInfo) {
    val headerBytes = header.encode()

    mainStream.writeVarInt32(headerBytes.size)
    mainStream.write(headerBytes)
  }

  @Throws(IOException::class)
  override fun write(frame: Frame) {
    val frameBytes: ByteArray = frame.encode()

    mainStream.writeVarInt32(frameBytes.size)
    mainStream.write(frameBytes)
  }

  @Throws(IOException::class)
  override fun close() {
    // We need to close the main stream in order for the gzip and all the cipher operations to fully finish before
    // we can calculate the MAC. Unfortunately flush()/finish() is not sufficient. So we have to defer to the
    // caller to append the bytes to the end of the data however they see fit (like appending to a file).
    mainStream.close()
    val mac = macStream.mac.doFinal()
    append(mac)
  }
}
