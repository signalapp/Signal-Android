package org.whispersystems.signalservice.api.crypto

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import assertk.fail
import org.conscrypt.Conscrypt
import org.junit.Assert
import org.junit.Test
import org.signal.core.util.StreamUtil
import org.signal.core.util.readFully
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.incrementalmac.ChunkSizeChoice
import org.signal.libsignal.protocol.incrementalmac.InvalidMacException
import org.signal.libsignal.protocol.kdf.HKDF
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream.IntegrityCheck
import org.whispersystems.signalservice.api.crypto.AttachmentCipherTestHelper.createMediaKeyMaterial
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream
import org.whispersystems.signalservice.internal.push.http.AttachmentCipherOutputStreamFactory
import org.whispersystems.signalservice.internal.util.Util
import org.whispersystems.signalservice.testutil.LibSignalLibraryUtil
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.lang.AssertionError
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.Security
import java.util.Random

class AttachmentCipherTest {
  @Test
  fun attachment_encryptDecrypt_nonIncremental_encryptedDigest() {
    attachment_encryptDecrypt(incremental = false, fileSize = MEBIBYTE, integrityCheckMode = IntegrityCheckMode.ENCRYPTED_DIGEST)
  }

  @Test
  fun attachment_encryptDecrypt_nonIncremental_plaintextHash() {
    attachment_encryptDecrypt(incremental = false, fileSize = MEBIBYTE, integrityCheckMode = IntegrityCheckMode.PLAINTEXT_HASH)
  }

  @Test
  fun attachment_encryptDecrypt_nonIncremental_bothIntegrityChecks() {
    attachment_encryptDecrypt(incremental = false, fileSize = MEBIBYTE, integrityCheckMode = IntegrityCheckMode.BOTH)
  }

  @Test
  fun attachment_encryptDecrypt_incremental_encryptedDigest() {
    attachment_encryptDecrypt(incremental = true, fileSize = MEBIBYTE, integrityCheckMode = IntegrityCheckMode.ENCRYPTED_DIGEST)
  }

  @Test
  fun attachment_encryptDecrypt_incremental_plaintextHash() {
    attachment_encryptDecrypt(incremental = true, fileSize = MEBIBYTE, integrityCheckMode = IntegrityCheckMode.PLAINTEXT_HASH)
  }

  @Test
  fun attachment_encryptDecrypt_incremental_bothIntegrityChecks() {
    attachment_encryptDecrypt(incremental = true, fileSize = MEBIBYTE, integrityCheckMode = IntegrityCheckMode.BOTH)
  }

  @Test
  fun attachment_encryptDecrypt_nonIncremental_manyFileSizes() {
    for (i in 0..99) {
      attachment_encryptDecrypt(incremental = false, fileSize = MEBIBYTE + Random().nextInt(1, 64 * 1024), integrityCheckMode = IntegrityCheckMode.BOTH)
    }
  }

  @Test
  fun attachment_encryptDecrypt_incremental_manyFileSizes() {
    // Designed to stress the various boundary conditions of reading the final mac
    for (i in 0..99) {
      attachment_encryptDecrypt(incremental = true, fileSize = MEBIBYTE + Random().nextInt(1, 64 * 1024), IntegrityCheckMode.BOTH)
    }
  }

  private fun attachment_encryptDecrypt(incremental: Boolean, fileSize: Int, integrityCheckMode: IntegrityCheckMode) {
    val key = Util.getSecretBytes(64)
    val plaintextInput = Util.getSecretBytes(fileSize)
    val plaintextHash = MessageDigest.getInstance("SHA-256").digest(plaintextInput)

    val encryptResult = encryptData(plaintextInput, key, incremental)
    val cipherFile = writeToFile(encryptResult.ciphertext)

    val integrityCheck = when (integrityCheckMode) {
      IntegrityCheckMode.ENCRYPTED_DIGEST -> IntegrityCheck.forEncryptedDigest(encryptResult.digest)
      IntegrityCheckMode.PLAINTEXT_HASH -> IntegrityCheck.forPlaintextHash(plaintextHash)
      IntegrityCheckMode.BOTH -> IntegrityCheck(
        encryptedDigest = encryptResult.digest,
        plaintextHash = plaintextHash
      )
    }
    val inputStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), key, integrityCheck, encryptResult.incrementalDigest, encryptResult.chunkSizeChoice)
    val plaintextOutput = inputStream.readFully()

    assertThat(plaintextOutput).isEqualTo(plaintextInput)

    cipherFile.delete()
  }

  @Test
  fun attachment_encryptDecrypt_skipAll_manyFileSizes() {
    for (i in 0..99) {
      attachment_encryptDecrypt_skipAll(incremental = false, fileSize = MEBIBYTE + Random().nextInt(1, 64 * 1024))
    }
  }

  private fun attachment_encryptDecrypt_skipAll(incremental: Boolean, fileSize: Int) {
    val key = Util.getSecretBytes(64)
    val plaintextInput = Util.getSecretBytes(fileSize)
    val plaintextHash = MessageDigest.getInstance("SHA-256").digest(plaintextInput)

    val encryptResult = encryptData(plaintextInput, key, incremental)
    val cipherFile = writeToFile(encryptResult.ciphertext)

    val integrityCheck = IntegrityCheck(
      encryptedDigest = encryptResult.digest,
      plaintextHash = plaintextHash
    )
    val inputStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), key, integrityCheck, encryptResult.incrementalDigest, encryptResult.chunkSizeChoice)
    while (inputStream.skip(cipherFile.length()) > 0) {
      // Empty body, just skipping
    }
    cipherFile.delete()
  }

  private fun attachment_encryptDecrypt_plaintextHash(incremental: Boolean, fileSize: Int) {
    val key = Util.getSecretBytes(64)
    val plaintextInput = Util.getSecretBytes(fileSize)
    val plaintextHash = MessageDigest.getInstance("SHA-256").digest(plaintextInput)

    val encryptResult = encryptData(plaintextInput, key, incremental)
    val cipherFile = writeToFile(encryptResult.ciphertext)

    val integrityCheck = IntegrityCheck.forPlaintextHash(plaintextHash)
    val inputStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), key, integrityCheck, encryptResult.incrementalDigest, encryptResult.chunkSizeChoice)
    val plaintextOutput = inputStream.readFully()

    assertThat(plaintextOutput).isEqualTo(plaintextInput)

    cipherFile.delete()
  }

  @Test
  fun attachment_encryptDecryptEmpty_nonIncremental() {
    attachment_encryptDecryptEmpty(incremental = false)
  }

  @Test
  fun attachment_encryptDecryptEmpty_incremental() {
    attachment_encryptDecryptEmpty(incremental = true)
  }

  private fun attachment_encryptDecryptEmpty(incremental: Boolean) {
    val key = Util.getSecretBytes(64)
    val plaintextInput = "".toByteArray()

    val encryptResult = encryptData(plaintextInput, key, incremental)
    val cipherFile = writeToFile(encryptResult.ciphertext)

    val integrityCheck = IntegrityCheck.forEncryptedDigest(encryptResult.digest)
    val inputStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), key, integrityCheck, encryptResult.incrementalDigest, encryptResult.chunkSizeChoice)
    val plaintextOutput = inputStream.readFully()

    Assert.assertArrayEquals(plaintextInput, plaintextOutput)

    cipherFile.delete()
  }

  @Test(expected = InvalidMessageException::class)
  fun attachment_decryptFailOnBadKey_nonIncremental() {
    attachment_decryptFailOnBadKey(incremental = false)
  }

  @Test(expected = InvalidMessageException::class)
  fun attachment_decryptFailOnBadKey_incremental() {
    attachment_decryptFailOnBadKey(incremental = true)
  }

  private fun attachment_decryptFailOnBadKey(incremental: Boolean) {
    var cipherFile: File? = null

    try {
      val key = Util.getSecretBytes(64)
      val plaintextInput = Util.getSecretBytes(MEBIBYTE)

      val encryptResult = encryptData(plaintextInput, key, incremental)
      cipherFile = writeToFile(encryptResult.ciphertext)

      val badKey = ByteArray(64)
      val integrityCheck = IntegrityCheck.forEncryptedDigest(encryptResult.digest)
      AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), badKey, integrityCheck, null, 0)
    } finally {
      cipherFile?.delete()
    }
  }

  @Test(expected = InvalidMessageException::class)
  fun attachment_decryptFailOnBadMac_nonIncremental() {
    attachment_decryptFailOnBadMac(incremental = false)
  }

  @Test(expected = InvalidMessageException::class)
  fun attachment_decryptFailOnBadMac_incremental() {
    attachment_decryptFailOnBadMac(incremental = true)
  }

  private fun attachment_decryptFailOnBadMac(incremental: Boolean) {
    var cipherFile: File? = null

    try {
      val key = Util.getSecretBytes(64)
      val plaintextInput = Util.getSecretBytes(MEBIBYTE)

      val encryptResult = encryptData(plaintextInput, key, incremental)
      val badMacCiphertext = encryptResult.ciphertext.copyOf(encryptResult.ciphertext.size)

      badMacCiphertext[badMacCiphertext.size - 1] = (badMacCiphertext[badMacCiphertext.size - 1] + 1).toByte()

      cipherFile = writeToFile(badMacCiphertext)

      val integrityCheck = IntegrityCheck.forEncryptedDigest(encryptResult.digest)
      val stream: InputStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), key, integrityCheck, encryptResult.incrementalDigest, encryptResult.chunkSizeChoice)

      // In incremental mode, we'll only check the digest after reading the whole thing
      if (incremental) {
        StreamUtil.readFully(stream)
      }
    } finally {
      cipherFile?.delete()
    }
  }

  @Test(expected = InvalidMessageException::class)
  fun attachment_decryptFailOnBadEncryptedDigest_nonIncremental() {
    attachment_decryptFailOnBadEncryptedDigest(incremental = false)
  }

  @Test(expected = InvalidMessageException::class)
  fun attachment_decryptFailOnBadEncryptedDigest_incremental() {
    attachment_decryptFailOnBadEncryptedDigest(incremental = true)
  }

  @Test(expected = InvalidMessageException::class)
  fun attachment_decryptFailOnBadPlaintextHash_nonIncremental() {
    attachment_decryptFailOnBadPlaintextHash(incremental = false)
  }

  @Test(expected = InvalidMessageException::class)
  fun attachment_decryptFailOnBadPlaintextHash_incremental() {
    attachment_decryptFailOnBadPlaintextHash(incremental = true)
  }

  private fun attachment_decryptFailOnBadEncryptedDigest(incremental: Boolean) {
    var cipherFile: File? = null

    try {
      val key = Util.getSecretBytes(64)
      val plaintextInput = Util.getSecretBytes(MEBIBYTE)

      val encryptResult = encryptData(plaintextInput, key, incremental)
      val badDigest = ByteArray(32)

      cipherFile = writeToFile(encryptResult.ciphertext)

      val integrityCheck = IntegrityCheck.forEncryptedDigest(badDigest)
      val stream: InputStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), key, integrityCheck, encryptResult.incrementalDigest, encryptResult.chunkSizeChoice)

      // In incremental mode, we'll only check the digest after reading the whole thing
      if (incremental) {
        StreamUtil.readFully(stream)
      }
    } finally {
      cipherFile?.delete()
    }
  }

  private fun attachment_decryptFailOnBadPlaintextHash(incremental: Boolean) {
    var cipherFile: File? = null

    try {
      val key = Util.getSecretBytes(64)
      val plaintextInput = Util.getSecretBytes(MEBIBYTE)
      val badPlaintextHash = MessageDigest.getInstance("SHA-256").digest(plaintextInput).apply {
        this[0] = (this[0] + 1).toByte()
      }

      val encryptResult = encryptData(plaintextInput, key, incremental)

      cipherFile = writeToFile(encryptResult.ciphertext)

      val integrityCheck = IntegrityCheck.forPlaintextHash(badPlaintextHash)
      val stream: InputStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), key, integrityCheck, encryptResult.incrementalDigest, encryptResult.chunkSizeChoice)

      StreamUtil.readFully(stream)
    } finally {
      cipherFile?.delete()
    }
  }

  @Test
  fun attachment_decryptFailOnBadIncrementalDigest() {
    var cipherFile: File? = null
    var hitCorrectException = false

    try {
      val key = Util.getSecretBytes(64)
      val plaintextInput = Util.getSecretBytes(MEBIBYTE)

      val encryptResult = encryptData(plaintextInput, key, true)
      val badDigest = Util.getSecretBytes(encryptResult.incrementalDigest.size)

      cipherFile = writeToFile(encryptResult.ciphertext)

      val integrityCheck = IntegrityCheck.forEncryptedDigest(encryptResult.digest)
      val decryptedStream: InputStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), key, integrityCheck, badDigest, encryptResult.chunkSizeChoice)
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
  fun archiveInnerAndOuterLayer_encryptDecryptEmpty() {
    val innerKey = Util.getSecretBytes(64)
    val plaintextInput = "".toByteArray()

    val innerEncryptResult = encryptData(plaintextInput, innerKey, withIncremental = false)
    val outerKey = Util.getSecretBytes(64)

    val outerEncryptResult = encryptData(innerEncryptResult.ciphertext, outerKey, false)
    val cipherFile = writeToFile(outerEncryptResult.ciphertext)

    val keyMaterial = createMediaKeyMaterial(outerKey)
    val decryptedStream = AttachmentCipherInputStream.createForArchivedMedia(
      archivedMediaKeyMaterial = keyMaterial,
      file = cipherFile,
      originalCipherTextLength = innerEncryptResult.ciphertext.size.toLong(),
      plaintextLength = plaintextInput.size.toLong(),
      combinedKeyMaterial = innerKey,
      plaintextHash = innerEncryptResult.plaintextHash,
      incrementalDigest = innerEncryptResult.incrementalDigest,
      incrementalMacChunkSize = innerEncryptResult.chunkSizeChoice
    )
    val plaintextOutput = decryptedStream.readFully()

    assertThat(plaintextOutput).isEqualTo(plaintextInput)

    cipherFile.delete()
  }

  @Test
  fun archiveInnerAndOuterLayer_decryptFailOnBadKey_nonIncremental() {
    archiveInnerAndOuterLayer_decryptFailOnBadKey(incremental = false)
  }

  @Test
  fun archiveInnerAndOuterLayer_decryptFailOnBadKey_incremental() {
    archiveInnerAndOuterLayer_decryptFailOnBadKey(incremental = true)
  }

  private fun archiveInnerAndOuterLayer_decryptFailOnBadKey(incremental: Boolean) {
    var cipherFile: File? = null
    var hitCorrectException = false

    try {
      val innerKey = Util.getSecretBytes(64)
      val badInnerKey = Util.getSecretBytes(64)
      val plaintextInput = "Gwen Stacy".toByteArray()

      val innerEncryptResult = encryptData(plaintextInput, innerKey, incremental)
      val outerKey = Util.getSecretBytes(64)

      val outerEncryptResult = encryptData(innerEncryptResult.ciphertext, outerKey, false)
      val cipherFile = writeToFile(outerEncryptResult.ciphertext)

      val keyMaterial = createMediaKeyMaterial(badInnerKey)

      AttachmentCipherInputStream.createForArchivedMedia(
        archivedMediaKeyMaterial = keyMaterial,
        file = cipherFile,
        originalCipherTextLength = innerEncryptResult.ciphertext.size.toLong(),
        plaintextLength = plaintextInput.size.toLong(),
        combinedKeyMaterial = innerKey,
        plaintextHash = innerEncryptResult.digest,
        incrementalDigest = innerEncryptResult.incrementalDigest,
        incrementalMacChunkSize = innerEncryptResult.chunkSizeChoice
      )
    } catch (e: InvalidMessageException) {
      hitCorrectException = true
    } finally {
      cipherFile?.delete()
    }

    assertThat(hitCorrectException).isTrue()
  }

  @Test
  fun archive_encryptDecrypt_nonIncremental() {
    archive_encryptDecrypt(incremental = false, fileSize = MEBIBYTE)
  }

  @Test
  fun archive_encryptDecrypt_incremental() {
    archive_encryptDecrypt(incremental = true, fileSize = MEBIBYTE)
  }

  @Test
  fun archive_encryptDecrypt_nonIncremental_manyFileSizes() {
    for (i in 0..99) {
      archive_encryptDecrypt(incremental = false, fileSize = MEBIBYTE + Random().nextInt(1, 64 * 1024))
    }
  }

  @Test
  fun archive_encryptDecrypt_incremental_manyFileSizes() {
    // Designed to stress the various boundary conditions of reading the final mac
    for (i in 0..99) {
      archive_encryptDecrypt(incremental = true, fileSize = MEBIBYTE + Random().nextInt(1, 64 * 1024))
    }
  }

  private fun archive_encryptDecrypt(incremental: Boolean, fileSize: Int) {
    val innerKey = Util.getSecretBytes(64)
    val plaintextInput = Util.getSecretBytes(fileSize)
    val innerEncryptResult = encryptData(plaintextInput, innerKey, incremental)

    val outerKey = Util.getSecretBytes(64)
    val outerEncryptResult = encryptData(innerEncryptResult.ciphertext, outerKey, withIncremental = false, padded = false) // Server doesn't pad
    val cipherFile = writeToFile(outerEncryptResult.ciphertext)

    val keyMaterial = createMediaKeyMaterial(outerKey)
    val decryptedStream = AttachmentCipherInputStream.createForArchivedMedia(
      archivedMediaKeyMaterial = keyMaterial,
      file = cipherFile,
      originalCipherTextLength = innerEncryptResult.ciphertext.size.toLong(),
      plaintextLength = plaintextInput.size.toLong(),
      combinedKeyMaterial = innerKey,
      plaintextHash = innerEncryptResult.plaintextHash,
      incrementalDigest = innerEncryptResult.incrementalDigest,
      incrementalMacChunkSize = innerEncryptResult.chunkSizeChoice
    )
    val plaintextOutput = decryptedStream.readFully()

    assertThat(plaintextOutput).isEqualTo(plaintextInput)

    cipherFile.delete()
  }

  @Test
  fun archiveThumbnail_encryptDecrypt_manyFileSizes() {
    for (i in 0..99) {
      archiveThumbnail_encryptDecrypt(fileSize = MEBIBYTE + Random().nextInt(1, 64 * 1024))
    }
  }

  private fun archiveThumbnail_encryptDecrypt(fileSize: Int) {
    val innerKey = Util.getSecretBytes(64)
    val plaintextInput = Util.getSecretBytes(fileSize)
    val innerEncryptResult = encryptData(plaintextInput, innerKey, withIncremental = false)

    val outerKey = Util.getSecretBytes(64)
    val outerEncryptResult = encryptData(innerEncryptResult.ciphertext, outerKey, withIncremental = false, padded = false) // Server doesn't pad
    val cipherFile = writeToFile(outerEncryptResult.ciphertext)

    val keyMaterial = createMediaKeyMaterial(outerKey)
    val decryptedStream = AttachmentCipherInputStream.createForArchivedThumbnail(
      archivedMediaKeyMaterial = keyMaterial,
      file = cipherFile,
      innerCombinedKeyMaterial = innerKey
    )
    val plaintextOutput = decryptedStream.readFully()

    // We knowingly keep padding on thumbnails, so for the test to work, we strip the padding off, but check to make sure the sizes match up beforehand
    assertThat(plaintextOutput.size).isEqualTo(PaddingInputStream.getPaddedSize(plaintextInput.size.toLong()).toInt())
    assertThat(plaintextOutput.copyOfRange(fromIndex = 0, toIndex = plaintextInput.size)).isEqualTo(plaintextInput)

    cipherFile.delete()
  }

  @Test
  fun archiveEncryptDecrypt_decryptFailOnInnerBadMac() {
    var cipherFile: File? = null
    var hitCorrectException = false

    try {
      val innerKey = Util.getSecretBytes(64)
      val plaintextInput = Util.getSecretBytes(MEBIBYTE)

      val innerEncryptResult = encryptData(plaintextInput, innerKey, withIncremental = true, padded = false) // Server doesn't pad
      val badMacInnerCipherText = innerEncryptResult.ciphertext.copyOf().also {
        it[it.size - 1] = (it[it.size - 1] + 1).toByte()
      }

      val outerKey = Util.getSecretBytes(64)
      val outerEncryptResult = encryptData(badMacInnerCipherText, outerKey, false)

      cipherFile = writeToFile(outerEncryptResult.ciphertext)

      val keyMaterial = createMediaKeyMaterial(innerKey)

      AttachmentCipherInputStream.createForArchivedMedia(
        archivedMediaKeyMaterial = keyMaterial,
        file = cipherFile,
        originalCipherTextLength = innerEncryptResult.ciphertext.size.toLong(),
        plaintextLength = plaintextInput.size.toLong(),
        combinedKeyMaterial = innerKey,
        plaintextHash = innerEncryptResult.digest,
        incrementalDigest = innerEncryptResult.incrementalDigest,
        incrementalMacChunkSize = innerEncryptResult.chunkSizeChoice
      )

      Assert.fail()
    } catch (e: InvalidMessageException) {
      hitCorrectException = true
    } finally {
      cipherFile?.delete()
    }

    Assert.assertTrue(hitCorrectException)
  }

  @Test
  fun archiveEncryptDecrypt_decryptFailOnOuterMac() {
    var cipherFile: File? = null
    var hitCorrectException = false

    try {
      val innerKey = Util.getSecretBytes(64)
      val plaintextInput = Util.getSecretBytes(MEBIBYTE)

      val innerEncryptResult = encryptData(plaintextInput, innerKey, withIncremental = true, padded = false) // Server doesn't pad
      val outerKey = Util.getSecretBytes(64)

      val outerEncryptResult = encryptData(innerEncryptResult.ciphertext, outerKey, false)
      val badMacOuterCiphertext = outerEncryptResult.ciphertext.copyOf().also {
        it[it.size - 1] = (it[it.size - 1] + 1).toByte()
      }

      cipherFile = writeToFile(badMacOuterCiphertext)

      val keyMaterial = createMediaKeyMaterial(innerKey)

      AttachmentCipherInputStream.createForArchivedMedia(
        archivedMediaKeyMaterial = keyMaterial,
        file = cipherFile,
        originalCipherTextLength = innerEncryptResult.ciphertext.size.toLong(),
        plaintextLength = plaintextInput.size.toLong(),
        combinedKeyMaterial = innerKey,
        plaintextHash = innerEncryptResult.digest,
        incrementalDigest = innerEncryptResult.incrementalDigest,
        incrementalMacChunkSize = innerEncryptResult.chunkSizeChoice
      )

      Assert.fail()
    } catch (e: InvalidMessageException) {
      hitCorrectException = true
    } finally {
      cipherFile?.delete()
    }

    Assert.assertTrue(hitCorrectException)
  }

  @Test
  fun archiveThumbnailEncryptDecrypt_decryptFailOnInnerBadMac() {
    var cipherFile: File? = null
    var hitCorrectException = false

    try {
      val innerKey = Util.getSecretBytes(64)
      val plaintextInput = Util.getSecretBytes(MEBIBYTE)

      val innerEncryptResult = encryptData(plaintextInput, innerKey, withIncremental = true, padded = false) // Server doesn't pad
      val badMacInnerCipherText = innerEncryptResult.ciphertext.copyOf().also {
        it[it.size - 1] = (it[it.size - 1] + 1).toByte()
      }

      val outerKey = Util.getSecretBytes(64)
      val outerEncryptResult = encryptData(badMacInnerCipherText, outerKey, false)

      cipherFile = writeToFile(outerEncryptResult.ciphertext)

      val keyMaterial = createMediaKeyMaterial(innerKey)

      AttachmentCipherInputStream.createForArchivedThumbnail(
        archivedMediaKeyMaterial = keyMaterial,
        file = cipherFile,
        innerCombinedKeyMaterial = innerKey
      ).readFully()

      Assert.fail()
    } catch (e: InvalidMessageException) {
      hitCorrectException = true
    } finally {
      cipherFile?.delete()
    }

    Assert.assertTrue(hitCorrectException)
  }

  @Test
  fun archiveThumbnailEncryptDecrypt_decryptFailOnOuterMac() {
    var cipherFile: File? = null
    var hitCorrectException = false

    try {
      val innerKey = Util.getSecretBytes(64)
      val plaintextInput = Util.getSecretBytes(MEBIBYTE)

      val innerEncryptResult = encryptData(plaintextInput, innerKey, withIncremental = true, padded = false) // Server doesn't pad
      val outerKey = Util.getSecretBytes(64)

      val outerEncryptResult = encryptData(innerEncryptResult.ciphertext, outerKey, false)
      val badMacOuterCiphertext = outerEncryptResult.ciphertext.copyOf().also {
        it[it.size - 1] = (it[it.size - 1] + 1).toByte()
      }

      cipherFile = writeToFile(badMacOuterCiphertext)

      val keyMaterial = createMediaKeyMaterial(innerKey)

      AttachmentCipherInputStream.createForArchivedThumbnail(
        archivedMediaKeyMaterial = keyMaterial,
        file = cipherFile,
        innerCombinedKeyMaterial = innerKey
      )

      Assert.fail()
    } catch (e: InvalidMessageException) {
      hitCorrectException = true
    } finally {
      cipherFile?.delete()
    }

    Assert.assertTrue(hitCorrectException)
  }

  @Test
  fun sticker_encryptDecrypt() {
    LibSignalLibraryUtil.assumeLibSignalSupportedOnOS()

    val packKey = Util.getSecretBytes(32)
    val plaintextInput = Util.getSecretBytes(MEBIBYTE)
    val encryptResult = encryptData(plaintextInput, expandPackKey(packKey), withIncremental = false, padded = false)
    val inputStream = AttachmentCipherInputStream.createForStickerData(encryptResult.ciphertext, packKey)
    val plaintextOutput = readInputStreamFully(inputStream)

    Assert.assertArrayEquals(plaintextInput, plaintextOutput)
  }

  @Test
  fun sticker_encryptDecryptEmpty() {
    LibSignalLibraryUtil.assumeLibSignalSupportedOnOS()

    val packKey = Util.getSecretBytes(32)
    val plaintextInput = "".toByteArray()
    val encryptResult = encryptData(plaintextInput, expandPackKey(packKey), withIncremental = false, padded = false)
    val inputStream = AttachmentCipherInputStream.createForStickerData(encryptResult.ciphertext, packKey)
    val plaintextOutput = readInputStreamFully(inputStream)

    Assert.assertArrayEquals(plaintextInput, plaintextOutput)
  }

  @Test
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
    val integrityCheck = IntegrityCheck.forEncryptedDigest(digest)
    val decryptedStream = AttachmentCipherInputStream.createForAttachment(cipherFile, combinedData.size.toLong(), key, integrityCheck, null, 0)
    val plaintextOutput = readInputStreamFully(decryptedStream)

    assertThat(plaintextOutput).isEqualTo(combinedData)
    cipherFile.delete()
  }

  @Test
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
    val integrityCheck = IntegrityCheck.forEncryptedDigest(digest)
    val decryptedStream = AttachmentCipherInputStream.createForAttachment(cipherFile, combinedData.size.toLong(), key, integrityCheck, null, 0)
    val plaintextOutput = readInputStreamFully(decryptedStream)

    assertThat(plaintextOutput).isEqualTo(combinedData)
    cipherFile.delete()
  }

  @Test
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
    val integrityCheck = IntegrityCheck.forEncryptedDigest(digest)
    val decryptedStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), key, integrityCheck, null, 0)
    val plaintextOutput = readInputStreamFully(decryptedStream)

    assertThat(plaintextOutput).isEqualTo(plaintextInput)
    cipherFile.delete()
  }

  @Test
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
    val integrityCheck = IntegrityCheck.forEncryptedDigest(digest)
    val decryptedStream = AttachmentCipherInputStream.createForAttachment(cipherFile, expectedData.size.toLong(), key, integrityCheck, null, 0)
    val plaintextOutput = readInputStreamFully(decryptedStream)

    assertThat(plaintextOutput).isEqualTo(expectedData)
    cipherFile.delete()
  }

  @Test
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
    val integrityCheck = IntegrityCheck.forEncryptedDigest(digest)
    val decryptedStream = AttachmentCipherInputStream.createForAttachment(cipherFile, plaintextInput.size.toLong(), key, integrityCheck, null, 0)
    val plaintextOutput = readInputStreamFully(decryptedStream)

    assertThat(plaintextOutput).isEqualTo(plaintextInput)
    cipherFile.delete()
  }

  private class EncryptResult(
    val ciphertext: ByteArray,
    val digest: ByteArray,
    val incrementalDigest: ByteArray,
    val chunkSizeChoice: Int,
    val plaintextHash: ByteArray
  )

  companion object {
    init {
      // https://github.com/google/conscrypt/issues/1034
      if (System.getProperty("os.arch") != "aarch64") {
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
      }
    }

    private const val MEBIBYTE = 1024 * 1024

    private fun encryptData(data: ByteArray, keyMaterial: ByteArray, withIncremental: Boolean, padded: Boolean = true): EncryptResult {
      val digestingStream = DigestInputStream(ByteArrayInputStream(data), MessageDigest.getInstance("SHA-256"))

      val actualData = if (padded) {
        PaddingInputStream(digestingStream, data.size.toLong()).readFully()
      } else {
        digestingStream.readFully()
      }

      val plaintextHash = digestingStream.messageDigest.digest()

      val outputStream = ByteArrayOutputStream()
      val incrementalDigestOut = ByteArrayOutputStream()
      val iv = Util.getSecretBytes(16)
      val factory = AttachmentCipherOutputStreamFactory(keyMaterial, iv)

      val encryptStream: DigestingOutputStream
      val sizeChoice = ChunkSizeChoice.inferChunkSize(actualData.size)
      encryptStream = if (withIncremental) {
        factory.createIncrementalFor(outputStream, actualData.size.toLong(), sizeChoice, incrementalDigestOut)
      } else {
        factory.createFor(outputStream)
      }

      encryptStream.write(actualData)
      encryptStream.flush()
      encryptStream.close()
      incrementalDigestOut.close()

      return EncryptResult(
        ciphertext = outputStream.toByteArray(),
        digest = encryptStream.transmittedDigest,
        incrementalDigest = incrementalDigestOut.toByteArray(),
        chunkSizeChoice = sizeChoice.sizeInBytes,
        plaintextHash = plaintextHash
      )
    }

    private fun writeToFile(data: ByteArray): File {
      val file = File.createTempFile("temp", ".data")
      val outputStream: OutputStream = FileOutputStream(file)

      outputStream.write(data)
      outputStream.close()

      return file
    }

    private fun readInputStreamFully(inputStream: InputStream): ByteArray {
      return Util.readFullyAsBytes(inputStream)
    }

    private fun expandPackKey(shortKey: ByteArray): ByteArray {
      return HKDF.deriveSecrets(shortKey, "Sticker Pack".toByteArray(), 64)
    }
  }

  enum class IntegrityCheckMode {
    ENCRYPTED_DIGEST,
    PLAINTEXT_HASH,
    BOTH
  }
}
