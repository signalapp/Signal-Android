/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.stream

import androidx.annotation.VisibleForTesting
import com.google.common.io.CountingInputStream
import org.signal.core.util.readFully
import org.signal.core.util.readNBytesOrThrow
import org.signal.core.util.readVarInt32
import org.signal.core.util.stream.LimitedInputStream
import org.signal.core.util.stream.MacInputStream
import org.signal.core.util.writeVarInt32
import org.signal.libsignal.messagebackup.BackupForwardSecrecyToken
import org.thoughtcrime.securesms.backup.v2.proto.BackupInfo
import org.thoughtcrime.securesms.backup.v2.proto.Frame
import org.whispersystems.signalservice.api.backup.MessageBackupKey
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Provides the ability to read backup frames in a streaming fashion from a target [InputStream].
 * As it's being read, it will be both decrypted and uncompressed. Specifically, the data is decrypted,
 * that decrypted data is gunzipped, then that data is read as frames.
 */
class EncryptedBackupReader private constructor(
  keyMaterial: MessageBackupKey.BackupKeyMaterial,
  val length: Long,
  dataStream: () -> InputStream
) : BackupImportReader {

  @VisibleForTesting
  val backupInfo: BackupInfo?
  private var next: Frame? = null
  private val stream: InputStream
  private val countingStream: CountingInputStream

  companion object {
    const val MAC_SIZE = 32

    /**
     * Estimated upperbound need to read backup secrecy metadata from the start of a file.
     *
     * Magic Number size + ~varint size (5) + forward secrecy metadata size estimate (200)
     */
    val BACKUP_SECRET_METADATA_UPPERBOUND = EncryptedBackupWriter.MAGIC_NUMBER.size + 5 + 200

    /**
     * Create a reader for a backup from the archive CDN.
     * The key difference is that we require forward secrecy data.
     */
    fun createForSignalBackup(
      key: MessageBackupKey,
      aci: ACI,
      forwardSecrecyToken: BackupForwardSecrecyToken,
      length: Long,
      dataStream: () -> InputStream
    ): EncryptedBackupReader {
      return EncryptedBackupReader(
        keyMaterial = key.deriveBackupSecrets(aci, forwardSecrecyToken),
        length = length,
        dataStream = dataStream
      )
    }

    /**
     * Create a reader for a local backup or for a transfer to a linked device. Basically everything that isn't [createForSignalBackup].
     * The key difference is that we don't require forward secrecy data.
     */
    fun createForLocalOrLinking(key: MessageBackupKey, aci: ACI, length: Long, dataStream: () -> InputStream): EncryptedBackupReader {
      return EncryptedBackupReader(
        keyMaterial = key.deriveBackupSecrets(aci, forwardSecrecyToken = null),
        length = length,
        dataStream = dataStream
      )
    }

    /**
     * Returns the size of the entire forward secrecy prefix. Includes the magic number, varint, and the length of the forward secrecy metadata itself.
     */
    fun getForwardSecrecyPrefixDataLength(stream: InputStream): Int {
      val metadataLength = readForwardSecrecyMetadata(stream)?.size ?: return 0
      return EncryptedBackupWriter.MAGIC_NUMBER.size + metadataLength.lengthAsVarInt32() + metadataLength
    }

    fun readForwardSecrecyMetadata(stream: InputStream): ByteArray? {
      val potentialMagicNumber = stream.readNBytesOrThrow(8)
      if (!EncryptedBackupWriter.MAGIC_NUMBER.contentEquals(potentialMagicNumber)) {
        return null
      }
      val metadataLength = stream.readVarInt32()
      return stream.readNBytesOrThrow(metadataLength)
    }

    private fun validateMac(macKey: ByteArray, streamLength: Long, dataStream: InputStream) {
      val mac = Mac.getInstance("HmacSHA256").apply {
        init(SecretKeySpec(macKey, "HmacSHA256"))
      }

      val macStream = MacInputStream(
        wrapped = LimitedInputStream(dataStream, maxBytes = streamLength - MAC_SIZE),
        mac = mac
      )

      macStream.readFully(false)

      val calculatedMac = macStream.mac.doFinal()
      val expectedMac = dataStream.readNBytesOrThrow(MAC_SIZE)

      if (!calculatedMac.contentEquals(expectedMac)) {
        throw IOException("Invalid MAC!")
      }
    }

    private fun Int.lengthAsVarInt32(): Int {
      return ByteArrayOutputStream().apply {
        writeVarInt32(this@lengthAsVarInt32)
      }.toByteArray().size
    }
  }

  init {
    val forwardSecrecyMetadata = dataStream().use { readForwardSecrecyMetadata(it) }

    val encryptedLength = if (forwardSecrecyMetadata != null) {
      val prefixLength = EncryptedBackupWriter.MAGIC_NUMBER.size + forwardSecrecyMetadata.size + forwardSecrecyMetadata.size.lengthAsVarInt32()
      length - prefixLength
    } else {
      length
    }

    val prefixSkippingStream = {
      if (forwardSecrecyMetadata == null) {
        dataStream()
      } else {
        dataStream().also { readForwardSecrecyMetadata(it) }
      }
    }

    prefixSkippingStream().use { validateMac(keyMaterial.macKey, encryptedLength, it) }

    countingStream = CountingInputStream(prefixSkippingStream())
    val iv = countingStream.readNBytesOrThrow(16)

    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
      init(Cipher.DECRYPT_MODE, SecretKeySpec(keyMaterial.aesKey, "AES"), IvParameterSpec(iv))
    }

    stream = GZIPInputStream(
      CipherInputStream(
        LimitedInputStream(
          wrapped = countingStream,
          maxBytes = encryptedLength - MAC_SIZE
        ),
        cipher
      )
    )
    backupInfo = readHeader()
    next = read()
  }

  override fun getHeader(): BackupInfo? {
    return backupInfo
  }

  override fun getBytesRead() = countingStream.count

  override fun getStreamLength() = length

  override fun hasNext(): Boolean {
    return next != null
  }

  override fun next(): Frame {
    next?.let { out ->
      next = read()
      return out
    } ?: throw NoSuchElementException()
  }

  private fun readHeader(): BackupInfo? {
    try {
      val length = stream.readVarInt32().takeIf { it >= 0 } ?: return null
      val headerBytes: ByteArray = stream.readNBytesOrThrow(length)

      return BackupInfo.ADAPTER.decode(headerBytes)
    } catch (e: EOFException) {
      return null
    }
  }

  private fun read(): Frame? {
    try {
      val length = stream.readVarInt32().also { if (it < 0) return null }
      val frameBytes: ByteArray = stream.readNBytesOrThrow(length)

      return Frame.ADAPTER.decode(frameBytes)
    } catch (e: EOFException) {
      return null
    }
  }

  override fun close() {
    stream.close()
  }
}
