/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.dependencies

import org.signal.libsignal.protocol.kdf.HKDF
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Port of the bits of `org.thoughtcrime.securesms.backup.BackupRecordInputStream` that the demo
 * ServerTask actually needs: reads the plaintext header to derive keys, then decrypts each
 * frame's length + payload so we can detect the `end = true` sentinel, and drains any attachment
 * body that follows a frame (attachments/stickers/avatars). Payloads are only parsed deeply
 * enough to spot the end marker and pull the body length out of attachment-ish frames.
 *
 * Hand-rolled protobuf parsing instead of wiring up Wire codegen in the demo module.
 */
class DemoBackupStreamReader(
  private val inputStream: InputStream,
  passphrase: String
) {

  private val cipher: Cipher = Cipher.getInstance("AES/CTR/NoPadding")
  private val mac: Mac = Mac.getInstance("HmacSHA256")
  private val cipherKey: ByteArray
  private val iv: ByteArray
  private val isFrameLengthEncrypted: Boolean
  private var counter: Int

  init {
    val headerLengthBytes = readFully(4)
    val headerLength = headerLengthBytes.toBigEndianInt()
    val headerBytes = readFully(headerLength)

    val header = parseHeader(headerBytes) ?: throw IOException("Backup stream does not start with a header frame")
    this.iv = header.iv.copyOf()
    if (iv.size != 16) throw IOException("Invalid IV length ${iv.size}")
    this.isFrameLengthEncrypted = header.version >= 1

    val key = deriveBackupKey(passphrase, header.salt)
    val derived = HKDF.deriveSecrets(key, "Backup Export".toByteArray(), 64)
    this.cipherKey = derived.copyOfRange(0, 32)
    val macKey = derived.copyOfRange(32, 64)
    this.mac.init(SecretKeySpec(macKey, "HmacSHA256"))

    this.counter = iv.toBigEndianInt()
  }

  /**
   * Reads one frame. Returns null if we've exhausted the stream. The returned [FrameInfo.end]
   * tells the caller to stop; [FrameInfo.attachmentBodyLength] tells them to also drain that
   * many attachment bytes via [drainAttachmentBody].
   */
  fun readFrame(): FrameInfo {
    val frameLength = decryptFrameLength()
    if (frameLength <= 0) throw IOException("Bogus decrypted frame length $frameLength — wrong passphrase?")

    val frame = readFully(frameLength)
    val payloadLength = frameLength - 10
    if (payloadLength < 0) throw IOException("Frame too small to contain MAC")

    val theirMac = frame.copyOfRange(payloadLength, frameLength)
    mac.update(frame, 0, payloadLength)
    val ourMac = mac.doFinal().copyOf(10)
    if (!MessageDigest.isEqual(ourMac, theirMac)) {
      throw IOException("Bad MAC on frame — wrong passphrase?")
    }

    val plaintext = cipher.doFinal(frame, 0, payloadLength)
    return parseFrame(plaintext)
  }

  /** Drains a post-frame attachment/sticker/avatar body of [length] bytes + its trailing MAC. */
  fun drainAttachmentBody(length: Int) {
    setCounter(counter++)
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(cipherKey, "AES"), IvParameterSpec(iv))
    mac.update(iv)

    val buffer = ByteArray(8192)
    var remaining = length
    while (remaining > 0) {
      val read = inputStream.read(buffer, 0, minOf(buffer.size, remaining))
      if (read < 0) throw EOFException("Stream closed mid-attachment (need $remaining more bytes)")
      mac.update(buffer, 0, read)
      cipher.update(buffer, 0, read)
      remaining -= read
    }
    cipher.doFinal()

    val ourMac = mac.doFinal().copyOf(10)
    val theirMac = readFully(10)
    if (!MessageDigest.isEqual(ourMac, theirMac)) {
      throw IOException("Bad attachment MAC")
    }
  }

  private fun decryptFrameLength(): Int {
    val lengthBytes = readFully(4)
    setCounter(counter++)
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(cipherKey, "AES"), IvParameterSpec(iv))

    return if (isFrameLengthEncrypted) {
      mac.update(lengthBytes)
      val decrypted = cipher.update(lengthBytes)
      if (decrypted == null || decrypted.size != 4) throw IOException("Cipher was not a stream cipher")
      decrypted.toBigEndianInt()
    } else {
      lengthBytes.toBigEndianInt()
    }
  }

  private fun setCounter(value: Int) {
    iv[0] = (value ushr 24).toByte()
    iv[1] = (value ushr 16).toByte()
    iv[2] = (value ushr 8).toByte()
    iv[3] = value.toByte()
  }

  private fun readFully(n: Int): ByteArray {
    val buf = ByteArray(n)
    var off = 0
    while (off < n) {
      val read = inputStream.read(buf, off, n - off)
      if (read < 0) throw EOFException("Stream closed after reading $off of $n")
      off += read
    }
    return buf
  }

  data class FrameInfo(val end: Boolean, val attachmentBodyLength: Int?)

  private data class ParsedHeader(val iv: ByteArray, val salt: ByteArray?, val version: Int)

  companion object {
    private const val DIGEST_ROUNDS = 250_000

    private fun deriveBackupKey(passphrase: String, salt: ByteArray?): ByteArray {
      val digest = MessageDigest.getInstance("SHA-512")
      val input = passphrase.replace(" ", "").toByteArray()
      var hash = input
      if (salt != null) digest.update(salt)
      repeat(DIGEST_ROUNDS) {
        digest.update(hash)
        hash = digest.digest(input)
      }
      return hash.copyOf(32)
    }

    private fun ByteArray.toBigEndianInt(): Int {
      require(size >= 4)
      return ((this[0].toInt() and 0xFF) shl 24) or
        ((this[1].toInt() and 0xFF) shl 16) or
        ((this[2].toInt() and 0xFF) shl 8) or
        (this[3].toInt() and 0xFF)
    }

    /** Parses a `BackupFrame { header = 1 }` wrapper and extracts Header fields. */
    private fun parseHeader(backupFrameBytes: ByteArray): ParsedHeader? {
      val outer = ProtoReader(backupFrameBytes)
      while (outer.hasRemaining()) {
        val (field, wireType) = outer.readTag()
        if (field == 1 && wireType == WIRE_LENGTH_DELIMITED) {
          val headerBytes = outer.readLengthDelimited()
          return parseInnerHeader(headerBytes)
        }
        outer.skip(wireType)
      }
      return null
    }

    private fun parseInnerHeader(bytes: ByteArray): ParsedHeader {
      val r = ProtoReader(bytes)
      var iv: ByteArray? = null
      var salt: ByteArray? = null
      var version = 0
      while (r.hasRemaining()) {
        val (field, wireType) = r.readTag()
        when (field) {
          1 -> iv = r.readLengthDelimited()
          2 -> salt = r.readLengthDelimited()
          3 -> version = r.readVarint().toInt()
          else -> r.skip(wireType)
        }
      }
      return ParsedHeader(iv ?: ByteArray(0), salt, version)
    }

    /** Inspects a decrypted BackupFrame for end-of-stream and attached-body length. */
    private fun parseFrame(frameBytes: ByteArray): FrameInfo {
      val r = ProtoReader(frameBytes)
      var end = false
      var bodyLength: Int? = null
      while (r.hasRemaining()) {
        val (field, wireType) = r.readTag()
        when (field) {
          4 -> { // attachment { length = 3 }
            val sub = r.readLengthDelimited()
            bodyLength = extractLengthField(sub, 3)
          }
          6 -> end = r.readVarint() != 0L // end (bool)
          7 -> { // avatar { length = 2 }
            val sub = r.readLengthDelimited()
            bodyLength = extractLengthField(sub, 2)
          }
          8 -> { // sticker { length = 2 }
            val sub = r.readLengthDelimited()
            bodyLength = extractLengthField(sub, 2)
          }
          else -> r.skip(wireType)
        }
      }
      return FrameInfo(end, bodyLength)
    }

    private fun extractLengthField(bytes: ByteArray, fieldNumber: Int): Int? {
      val r = ProtoReader(bytes)
      while (r.hasRemaining()) {
        val (field, wireType) = r.readTag()
        if (field == fieldNumber && wireType == WIRE_VARINT) return r.readVarint().toInt()
        r.skip(wireType)
      }
      return null
    }

    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val WIRE_FIXED32 = 5

    /** Minimal protobuf wire-format reader. Handles only the wire types we need. */
    private class ProtoReader(private val bytes: ByteArray) {
      private var pos: Int = 0

      fun hasRemaining(): Boolean = pos < bytes.size

      fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
          if (pos >= bytes.size) throw IOException("Truncated varint")
          val b = bytes[pos++].toInt() and 0xFF
          result = result or ((b and 0x7F).toLong() shl shift)
          if (b and 0x80 == 0) return result
          shift += 7
          if (shift >= 64) throw IOException("Varint too long")
        }
      }

      fun readTag(): Pair<Int, Int> {
        val tag = readVarint().toInt()
        return (tag ushr 3) to (tag and 0x07)
      }

      fun readLengthDelimited(): ByteArray {
        val len = readVarint().toInt()
        if (len < 0 || pos + len > bytes.size) throw IOException("Truncated length-delimited field (len=$len, remaining=${bytes.size - pos})")
        val result = bytes.copyOfRange(pos, pos + len)
        pos += len
        return result
      }

      fun skip(wireType: Int) {
        when (wireType) {
          WIRE_VARINT -> readVarint()
          WIRE_FIXED64 -> pos += 8
          WIRE_LENGTH_DELIMITED -> {
            val len = readVarint().toInt()
            pos += len
          }
          WIRE_FIXED32 -> pos += 4
          else -> throw IOException("Unknown wire type $wireType")
        }
      }
    }
  }
}
