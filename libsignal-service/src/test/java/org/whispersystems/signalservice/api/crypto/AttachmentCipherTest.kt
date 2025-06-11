package org.whispersystems.signalservice.api.crypto

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import assertk.fail
import org.conscrypt.Conscrypt
import org.junit.Assert
import org.junit.Test
import org.signal.core.util.StreamUtil
import org.signal.core.util.copyTo
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.incrementalmac.ChunkSizeChoice
import org.signal.libsignal.protocol.incrementalmac.InvalidMacException
import org.signal.libsignal.protocol.kdf.HKDF
import org.whispersystems.signalservice.api.crypto.AttachmentCipherTestHelper.createMediaKeyMaterial
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import org.whispersystems.signalservice.internal.push.http.AttachmentCipherOutputStreamFactory
import org.whispersystems.signalservice.internal.util.Util
import org.whispersystems.signalservice.testutil.LibSignalLibraryUtil
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.AssertionError
import java.security.Security
import java.util.Random

class AttachmentCipherTest {
  @Test
  @Throws(IOException::class, InvalidMessageException::class)
  fun attachment_encryptDecrypt_nonIncremental() {
    attachment_encryptDecrypt(incremental = false, fileSize = MEBIBYTE)
  }

  @Test
  @Throws(IOException::class, InvalidMessageException::class)
  fun attachment_encryptDecrypt_incremental() {
    attachment_encryptDecrypt(incremental = true, fileSize = MEBIBYTE)
  }

  @Test
  @Throws(IOException::class, InvalidMessageException::class)
  fun attachment_encryptDecrypt_incremental_manyFileSizes() {
    // Designed to stress the various boundary conditions of reading the final mac
    for (i in 0..99) {
      attachment_encryptDecrypt(incremental = true, fileSize = MEBIBYTE + Random().nextInt(1, 64 * 1024))
    }
  }

  @Throws(IOException::class, InvalidMessageException::class)
  private fun attachment_encryptDecrypt(incremental: Boolean, fileSize: Int) {
    val key = Util.getSecretBytes(64)
    val plaintextInput = Util.getSecretBytes(fileSize)

    val encryptResult = encryptData(plaintextInput, key, incremental)
    val cipherFile = writeToFile(encryptResult.ciphertext)

    val inputStream: InputStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), key, encryptResult.digest, encryptResult.incrementalDigest, encryptResult.chunkSizeChoice)
    val plaintextOutput = readInputStreamFully(inputStream)

    assertThat(plaintextOutput).isEqualTo(plaintextInput)

    cipherFile.delete()
  }

  @Test
  @Throws(IOException::class, InvalidMessageException::class)
  fun attachment_encryptDecryptEmpty_nonIncremental() {
    attachment_encryptDecryptEmpty(incremental = false)
  }

  @Test
  @Throws(IOException::class, InvalidMessageException::class)
  fun attachment_encryptDecryptEmpty_incremental() {
    attachment_encryptDecryptEmpty(incremental = true)
  }

  @Throws(IOException::class, InvalidMessageException::class)
  private fun attachment_encryptDecryptEmpty(incremental: Boolean) {
    val key = Util.getSecretBytes(64)
    val plaintextInput = "".toByteArray()

    val encryptResult = encryptData(plaintextInput, key, incremental)
    val cipherFile = writeToFile(encryptResult.ciphertext)

    val inputStream: InputStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), key, encryptResult.digest, encryptResult.incrementalDigest, encryptResult.chunkSizeChoice)
    val plaintextOutput = readInputStreamFully(inputStream)

    Assert.assertArrayEquals(plaintextInput, plaintextOutput)

    cipherFile.delete()
  }

  @Test(expected = InvalidMessageException::class)
  @Throws(IOException::class, InvalidMessageException::class)
  fun attachment_decryptFailOnBadKey_nonIncremental() {
    attachment_decryptFailOnBadKey(incremental = false)
  }

  @Test(expected = InvalidMessageException::class)
  @Throws(IOException::class, InvalidMessageException::class)
  fun attachment_decryptFailOnBadKey_incremental() {
    attachment_decryptFailOnBadKey(incremental = true)
  }

  @Throws(IOException::class, InvalidMessageException::class)
  private fun attachment_decryptFailOnBadKey(incremental: Boolean) {
    var cipherFile: File? = null

    try {
      val key = Util.getSecretBytes(64)
      val plaintextInput = Util.getSecretBytes(MEBIBYTE)

      val encryptResult = encryptData(plaintextInput, key, incremental)
      cipherFile = writeToFile(encryptResult.ciphertext)

      val badKey = ByteArray(64)
      AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), badKey, encryptResult.digest, null, 0)
    } finally {
      cipherFile?.delete()
    }
  }

  @Test(expected = InvalidMessageException::class)
  @Throws(IOException::class, InvalidMessageException::class)
  fun attachment_decryptFailOnBadMac_nonIncremental() {
    attachment_decryptFailOnBadMac(incremental = false)
  }

  @Test(expected = InvalidMessageException::class)
  @Throws(IOException::class, InvalidMessageException::class)
  fun attachment_decryptFailOnBadMac_incremental() {
    attachment_decryptFailOnBadMac(incremental = true)
  }

  @Throws(IOException::class, InvalidMessageException::class)
  private fun attachment_decryptFailOnBadMac(incremental: Boolean) {
    var cipherFile: File? = null

    try {
      val key = Util.getSecretBytes(64)
      val plaintextInput = Util.getSecretBytes(MEBIBYTE)

      val encryptResult = encryptData(plaintextInput, key, incremental)
      val badMacCiphertext = encryptResult.ciphertext.copyOf(encryptResult.ciphertext.size)

      badMacCiphertext[badMacCiphertext.size - 1] = (badMacCiphertext[badMacCiphertext.size - 1] + 1).toByte()

      cipherFile = writeToFile(badMacCiphertext)

      val stream: InputStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), key, encryptResult.digest, encryptResult.incrementalDigest, encryptResult.chunkSizeChoice)

      // In incremental mode, we'll only check the digest after reading the whole thing
      if (incremental) {
        StreamUtil.readFully(stream)
      }
    } finally {
      cipherFile?.delete()
    }
  }

  @Test(expected = InvalidMessageException::class)
  @Throws(IOException::class, InvalidMessageException::class)
  fun attachment_decryptFailOnNullDigest_nonIncremental() {
    attachment_decryptFailOnNullDigest(incremental = false)
  }

  @Test(expected = InvalidMessageException::class)
  @Throws(IOException::class, InvalidMessageException::class)
  fun attachment_decryptFailOnNullDigest_incremental() {
    attachment_decryptFailOnNullDigest(incremental = true)
  }

  @Throws(IOException::class, InvalidMessageException::class)
  private fun attachment_decryptFailOnNullDigest(incremental: Boolean) {
    var cipherFile: File? = null

    try {
      val key = Util.getSecretBytes(64)
      val plaintextInput = Util.getSecretBytes(MEBIBYTE)
      val encryptResult = encryptData(plaintextInput, key, incremental)

      cipherFile = writeToFile(encryptResult.ciphertext)

      AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), key, null, encryptResult.incrementalDigest, encryptResult.chunkSizeChoice)
    } finally {
      cipherFile?.delete()
    }
  }

  @Test(expected = InvalidMessageException::class)
  @Throws(IOException::class, InvalidMessageException::class)
  fun attachment_decryptFailOnBadDigest_nonIncremental() {
    attachment_decryptFailOnBadDigest(incremental = false)
  }

  @Test(expected = InvalidMessageException::class)
  @Throws(IOException::class, InvalidMessageException::class)
  fun attachment_decryptFailOnBadDigest_incremental() {
    attachment_decryptFailOnBadDigest(incremental = true)
  }

  @Throws(IOException::class, InvalidMessageException::class)
  private fun attachment_decryptFailOnBadDigest(incremental: Boolean) {
    var cipherFile: File? = null

    try {
      val key = Util.getSecretBytes(64)
      val plaintextInput = Util.getSecretBytes(MEBIBYTE)

      val encryptResult = encryptData(plaintextInput, key, incremental)
      val badDigest = ByteArray(32)

      cipherFile = writeToFile(encryptResult.ciphertext)

      val stream: InputStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), key, badDigest, encryptResult.incrementalDigest, encryptResult.chunkSizeChoice)

      // In incremental mode, we'll only check the digest after reading the whole thing
      if (incremental) {
        StreamUtil.readFully(stream)
      }
    } finally {
      cipherFile?.delete()
    }
  }

  @Test
  @Throws(IOException::class)
  fun attachment_decryptFailOnBadIncrementalDigest() {
    var cipherFile: File? = null
    var hitCorrectException = false

    try {
      val key = Util.getSecretBytes(64)
      val plaintextInput = Util.getSecretBytes(MEBIBYTE)

      val encryptResult = encryptData(plaintextInput, key, true)
      val badDigest = Util.getSecretBytes(encryptResult.incrementalDigest.size)

      cipherFile = writeToFile(encryptResult.ciphertext)

      val decryptedStream: InputStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), key, encryptResult.digest, badDigest, encryptResult.chunkSizeChoice)
      val plaintextOutput = readInputStreamFully(decryptedStream)

      fail(AssertionError("Expected to fail before hitting this line"))
    } catch (e: InvalidMacException) {
      hitCorrectException = true
    } catch (e: InvalidMessageException) {
      hitCorrectException = false
    } finally {
      cipherFile?.delete()
    }

    assertThat(hitCorrectException).isTrue()
  }

  @Test
  @Throws(IOException::class, InvalidMessageException::class)
  fun attachment_encryptDecryptPaddedContent() {
    val lengths = intArrayOf(531, 600, 724, 1019, 1024)

    for (length in lengths) {
      val plaintextInput = ByteArray(length)

      for (i in 0..<length) {
        plaintextInput[i] = 0x97.toByte()
      }

      val key = Util.getSecretBytes(64)
      val iv = Util.getSecretBytes(16)
      val inputStream = ByteArrayInputStream(plaintextInput)
      val paddedInputStream = PaddingInputStream(inputStream, length.toLong())
      val destinationOutputStream = ByteArrayOutputStream()

      val encryptingOutputStream = AttachmentCipherOutputStreamFactory(key, iv).createFor(destinationOutputStream)

      paddedInputStream.copyTo(encryptingOutputStream)

      val encryptedData = destinationOutputStream.toByteArray()
      val digest = encryptingOutputStream.transmittedDigest

      val cipherFile = writeToFile(encryptedData)

      val decryptedStream: InputStream = AttachmentCipherInputStream.createForAttachment(cipherFile, length.toLong(), key, digest, null, 0)
      val plaintextOutput = readInputStreamFully(decryptedStream)

      assertThat(plaintextOutput).isEqualTo(plaintextInput)

      cipherFile.delete()
    }
  }

  @Test
  @Throws(IOException::class, InvalidMessageException::class)
  fun archive_encryptDecrypt() {
    val key = Util.getSecretBytes(64)
    val keyMaterial = createMediaKeyMaterial(key)
    val plaintextInput = "Peter Parker".toByteArray()

    val encryptResult = encryptData(plaintextInput, key, false)
    val cipherFile = writeToFile(encryptResult.ciphertext)

    val inputStream = AttachmentCipherInputStream.createForArchivedMedia(keyMaterial, cipherFile, plaintextInput.size.toLong())
    val plaintextOutput = readInputStreamFully(inputStream)

    assertThat(plaintextOutput).isEqualTo(plaintextInput)

    cipherFile.delete()
  }

  @Test
  @Throws(IOException::class, InvalidMessageException::class)
  fun archive_encryptDecryptEmpty() {
    val key = Util.getSecretBytes(64)
    val keyMaterial = createMediaKeyMaterial(key)
    val plaintextInput = "".toByteArray()

    val encryptResult = encryptData(plaintextInput, key, false)
    val cipherFile = writeToFile(encryptResult.ciphertext)

    val inputStream: InputStream = AttachmentCipherInputStream.createForArchivedMedia(keyMaterial, cipherFile, plaintextInput.size.toLong())
    val plaintextOutput = readInputStreamFully(inputStream)

    assertThat(plaintextOutput).isEqualTo(plaintextInput)

    cipherFile.delete()
  }

  @Test
  @Throws(IOException::class)
  fun archive_decryptFailOnBadKey() {
    var cipherFile: File? = null
    var hitCorrectException = false

    try {
      val key = Util.getSecretBytes(64)
      val badKey = Util.getSecretBytes(64)
      val keyMaterial = createMediaKeyMaterial(badKey)
      val plaintextInput = "Gwen Stacy".toByteArray()

      val encryptResult = encryptData(plaintextInput, key, false)
      cipherFile = writeToFile(encryptResult.ciphertext)

      AttachmentCipherInputStream.createForArchivedMedia(keyMaterial, cipherFile, plaintextInput.size.toLong())
    } catch (e: InvalidMessageException) {
      hitCorrectException = true
    } finally {
      cipherFile?.delete()
    }

    assertThat(hitCorrectException).isTrue()
  }

  @Test
  @Throws(IOException::class, InvalidMessageException::class)
  fun archive_encryptDecryptPaddedContent() {
    val lengths = intArrayOf(531, 600, 724, 1019, 1024)

    for (length in lengths) {
      val plaintextInput = ByteArray(length)

      for (i in 0..<length) {
        plaintextInput[i] = 0x97.toByte()
      }

      val key = Util.getSecretBytes(64)
      val iv = Util.getSecretBytes(16)
      val inputStream = ByteArrayInputStream(plaintextInput)
      val paddedInputStream = PaddingInputStream(inputStream, length.toLong())
      val destinationOutputStream = ByteArrayOutputStream()

      val encryptingOutputStream = AttachmentCipherOutputStreamFactory(key, iv).createFor(destinationOutputStream)

      paddedInputStream.copyTo(encryptingOutputStream)

      val encryptedData = destinationOutputStream.toByteArray()

      val cipherFile = writeToFile(encryptedData)

      val keyMaterial = createMediaKeyMaterial(key)
      val decryptedStream: InputStream = AttachmentCipherInputStream.createForArchivedMedia(keyMaterial, cipherFile, length.toLong())
      val plaintextOutput = readInputStreamFully(decryptedStream)

      Assert.assertArrayEquals(plaintextInput, plaintextOutput)

      cipherFile.delete()
    }
  }

  @Test
  @Throws(IOException::class)
  fun archive_decryptFailOnBadMac() {
    var cipherFile: File? = null
    var hitCorrectException = false

    try {
      val key = Util.getSecretBytes(64)
      val plaintextInput = Util.getSecretBytes(MEBIBYTE)
      val encryptResult = encryptData(plaintextInput, key, true)
      val badMacCiphertext = encryptResult.ciphertext.copyOf(encryptResult.ciphertext.size)

      badMacCiphertext[badMacCiphertext.size - 1] = (badMacCiphertext[badMacCiphertext.size - 1] + 1).toByte()

      cipherFile = writeToFile(badMacCiphertext)

      val keyMaterial = createMediaKeyMaterial(key)
      AttachmentCipherInputStream.createForArchivedMedia(keyMaterial, cipherFile, plaintextInput.size.toLong())
      Assert.fail()
    } catch (e: InvalidMessageException) {
      hitCorrectException = true
    } finally {
      cipherFile?.delete()
    }

    Assert.assertTrue(hitCorrectException)
  }

  @Test
  @Throws(IOException::class, InvalidMessageException::class)
  fun sticker_encryptDecrypt() {
    LibSignalLibraryUtil.assumeLibSignalSupportedOnOS()

    val packKey = Util.getSecretBytes(32)
    val plaintextInput = Util.getSecretBytes(MEBIBYTE)
    val encryptResult = encryptData(plaintextInput, expandPackKey(packKey), true)
    val inputStream = AttachmentCipherInputStream.createForStickerData(encryptResult.ciphertext, packKey)
    val plaintextOutput = readInputStreamFully(inputStream)

    Assert.assertArrayEquals(plaintextInput, plaintextOutput)
  }

  @Test
  @Throws(IOException::class, InvalidMessageException::class)
  fun sticker_encryptDecryptEmpty() {
    LibSignalLibraryUtil.assumeLibSignalSupportedOnOS()

    val packKey = Util.getSecretBytes(32)
    val plaintextInput = "".toByteArray()
    val encryptResult = encryptData(plaintextInput, expandPackKey(packKey), true)
    val inputStream = AttachmentCipherInputStream.createForStickerData(encryptResult.ciphertext, packKey)
    val plaintextOutput = readInputStreamFully(inputStream)

    Assert.assertArrayEquals(plaintextInput, plaintextOutput)
  }

  @Test
  @Throws(IOException::class)
  fun sticker_decryptFailOnBadKey() {
    LibSignalLibraryUtil.assumeLibSignalSupportedOnOS()

    var hitCorrectException = false

    try {
      val packKey = Util.getSecretBytes(32)
      val plaintextInput = Util.getSecretBytes(MEBIBYTE)
      val encryptResult = encryptData(plaintextInput, expandPackKey(packKey), true)
      val badPackKey = ByteArray(32)

      AttachmentCipherInputStream.createForStickerData(encryptResult.ciphertext, badPackKey)
    } catch (e: InvalidMessageException) {
      hitCorrectException = true
    }

    Assert.assertTrue(hitCorrectException)
  }

  @Test
  @Throws(IOException::class)
  fun sticker_decryptFailOnBadMac() {
    LibSignalLibraryUtil.assumeLibSignalSupportedOnOS()

    var hitCorrectException = false

    try {
      val packKey = Util.getSecretBytes(32)
      val plaintextInput = Util.getSecretBytes(MEBIBYTE)
      val encryptResult = encryptData(plaintextInput, expandPackKey(packKey), true)
      val badMacCiphertext = encryptResult.ciphertext.copyOf(encryptResult.ciphertext.size)

      badMacCiphertext[badMacCiphertext.size - 1] = (badMacCiphertext[badMacCiphertext.size - 1] + 1).toByte()

      AttachmentCipherInputStream.createForStickerData(badMacCiphertext, packKey)
    } catch (e: InvalidMessageException) {
      hitCorrectException = true
    }

    Assert.assertTrue(hitCorrectException)
  }

  @Test
  @Throws(IOException::class)
  fun outputStream_writeAfterFlush() {
    val key = Util.getSecretBytes(64)
    val iv = Util.getSecretBytes(16)
    val plaintextInput1 = Util.getSecretBytes(MEBIBYTE)
    val plaintextInput2 = Util.getSecretBytes(MEBIBYTE)

    val destinationOutputStream = ByteArrayOutputStream()
    val encryptingOutputStream = AttachmentCipherOutputStreamFactory(key, iv).createFor(destinationOutputStream)

    encryptingOutputStream.write(plaintextInput1)
    encryptingOutputStream.flush()

    encryptingOutputStream.write(plaintextInput2)
    encryptingOutputStream.close()

    val encryptedData = destinationOutputStream.toByteArray()
    val digest = encryptingOutputStream.transmittedDigest
    val combinedData = plaintextInput1 + plaintextInput2

    val cipherFile = writeToFile(encryptedData)
    val decryptedStream = AttachmentCipherInputStream.createForAttachment(cipherFile, combinedData.size.toLong(), key, digest, null, 0)
    val plaintextOutput = readInputStreamFully(decryptedStream)

    assertThat(plaintextOutput).isEqualTo(combinedData)
    cipherFile.delete()
  }

  @Test
  @Throws(IOException::class)
  fun outputStream_flushMultipleTimes() {
    val key = Util.getSecretBytes(64)
    val iv = Util.getSecretBytes(16)
    val plaintextInput1 = Util.getSecretBytes(MEBIBYTE)
    val plaintextInput2 = Util.getSecretBytes(MEBIBYTE)

    val destinationOutputStream = ByteArrayOutputStream()
    val encryptingOutputStream = AttachmentCipherOutputStreamFactory(key, iv).createFor(destinationOutputStream)

    encryptingOutputStream.write(plaintextInput1)

    encryptingOutputStream.flush()
    encryptingOutputStream.flush()
    encryptingOutputStream.flush()

    encryptingOutputStream.write(plaintextInput2)
    encryptingOutputStream.close()

    val encryptedData = destinationOutputStream.toByteArray()
    val digest = encryptingOutputStream.transmittedDigest
    val combinedData = plaintextInput1 + plaintextInput2

    val cipherFile = writeToFile(encryptedData)
    val decryptedStream = AttachmentCipherInputStream.createForAttachment(cipherFile, combinedData.size.toLong(), key, digest, null, 0)
    val plaintextOutput = readInputStreamFully(decryptedStream)

    assertThat(plaintextOutput).isEqualTo(combinedData)
    cipherFile.delete()
  }

  @Test
  @Throws(IOException::class)
  fun outputStream_singleByteWrite() {
    val key = Util.getSecretBytes(64)
    val iv = Util.getSecretBytes(16)
    val plaintextInput = Util.getSecretBytes(MEBIBYTE)
    val destinationOutputStream = ByteArrayOutputStream()
    val encryptingOutputStream = AttachmentCipherOutputStreamFactory(key, iv).createFor(destinationOutputStream)

    for (b in plaintextInput) {
      encryptingOutputStream.write(b.toInt())
    }

    encryptingOutputStream.close()

    val encryptedData = destinationOutputStream.toByteArray()
    val digest = encryptingOutputStream.transmittedDigest

    val cipherFile = writeToFile(encryptedData)
    val decryptedStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), key, digest, null, 0)
    val plaintextOutput = readInputStreamFully(decryptedStream)

    assertThat(plaintextOutput).isEqualTo(plaintextInput)
    cipherFile.delete()
  }

  @Test
  @Throws(IOException::class)
  fun outputStream_mixedSingleByteAndArrayWrites() {
    val key = Util.getSecretBytes(64)
    val iv = Util.getSecretBytes(16)
    val plaintextInput1 = Util.getSecretBytes(512)
    val plaintextInput2 = Util.getSecretBytes(512)
    val destinationOutputStream = ByteArrayOutputStream()
    val encryptingOutputStream = AttachmentCipherOutputStreamFactory(key, iv).createFor(destinationOutputStream)

    // Write first part as array
    encryptingOutputStream.write(plaintextInput1)

    // Write second part one byte at a time
    for (b in plaintextInput2) {
      encryptingOutputStream.write(b.toInt())
    }

    encryptingOutputStream.close()

    val expectedData = plaintextInput1 + plaintextInput2
    val encryptedData = destinationOutputStream.toByteArray()
    val digest = encryptingOutputStream.transmittedDigest

    val cipherFile = writeToFile(encryptedData)
    val decryptedStream = AttachmentCipherInputStream.createForAttachment(cipherFile, expectedData.size.toLong(), key, digest, null, 0)
    val plaintextOutput = readInputStreamFully(decryptedStream)

    assertThat(plaintextOutput).isEqualTo(expectedData)
    cipherFile.delete()
  }

  @Test
  @Throws(IOException::class)
  fun outputStream_singleByteWriteWithFlushes() {
    val key = Util.getSecretBytes(64)
    val iv = Util.getSecretBytes(16)
    val plaintextInput = Util.getSecretBytes(256)
    val destinationOutputStream = ByteArrayOutputStream()
    val encryptingOutputStream = AttachmentCipherOutputStreamFactory(key, iv).createFor(destinationOutputStream)

    // Write bytes one at a time with occasional flushes
    for (i in plaintextInput.indices) {
      encryptingOutputStream.write(plaintextInput[i].toInt())
      if (i % 64 == 0) {
        encryptingOutputStream.flush()
      }
    }

    encryptingOutputStream.close()

    val encryptedData = destinationOutputStream.toByteArray()
    val digest = encryptingOutputStream.transmittedDigest

    val cipherFile = writeToFile(encryptedData)
    val decryptedStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), key, digest, null, 0)
    val plaintextOutput = readInputStreamFully(decryptedStream)

    assertThat(plaintextOutput).isEqualTo(plaintextInput)
    cipherFile.delete()
  }

  private class EncryptResult(val ciphertext: ByteArray, val digest: ByteArray, val incrementalDigest: ByteArray, val chunkSizeChoice: Int)
  companion object {
    init {
      // https://github.com/google/conscrypt/issues/1034
      if (System.getProperty("os.arch") != "aarch64") {
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
      }
    }

    private const val MEBIBYTE = 1024 * 1024

    @Throws(IOException::class)
    private fun encryptData(data: ByteArray, keyMaterial: ByteArray, withIncremental: Boolean): EncryptResult {
      val outputStream = ByteArrayOutputStream()
      val incrementalDigestOut = ByteArrayOutputStream()
      val iv = Util.getSecretBytes(16)
      val factory = AttachmentCipherOutputStreamFactory(keyMaterial, iv)

      val encryptStream: DigestingOutputStream
      val sizeChoice = ChunkSizeChoice.inferChunkSize(data.size)
      encryptStream = if (withIncremental) {
        factory.createIncrementalFor(outputStream, data.size.toLong(), sizeChoice, incrementalDigestOut)
      } else {
        factory.createFor(outputStream)
      }

      encryptStream.write(data)
      encryptStream.flush()
      encryptStream.close()
      incrementalDigestOut.close()

      return EncryptResult(outputStream.toByteArray(), encryptStream.transmittedDigest, incrementalDigestOut.toByteArray(), sizeChoice.sizeInBytes)
    }

    @Throws(IOException::class)
    private fun writeToFile(data: ByteArray): File {
      val file = File.createTempFile("temp", ".data")
      val outputStream: OutputStream = FileOutputStream(file)

      outputStream.write(data)
      outputStream.close()

      return file
    }

    @Throws(IOException::class)
    private fun readInputStreamFully(inputStream: InputStream): ByteArray {
      return Util.readFullyAsBytes(inputStream)
    }

    private fun expandPackKey(shortKey: ByteArray): ByteArray {
      return HKDF.deriveSecrets(shortKey, "Sticker Pack".toByteArray(), 64)
    }
  }
}
