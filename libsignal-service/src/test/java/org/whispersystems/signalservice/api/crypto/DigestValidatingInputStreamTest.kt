package org.whispersystems.signalservice.api.crypto

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import assertk.fail
import org.junit.Test
import org.signal.core.util.readFully
import org.signal.libsignal.protocol.InvalidMessageException
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class DigestValidatingInputStreamTest {

  @Test
  fun `success - read byte by byte`() {
    val data = "Hello, World!".toByteArray()
    val digest = MessageDigest.getInstance("SHA-256")
    val expectedHash = digest.digest(data)

    val inputStream = ByteArrayInputStream(data)
    val digestEnforcingStream = DigestValidatingInputStream(
      inputStream = inputStream,
      digest = MessageDigest.getInstance("SHA-256"),
      expectedHash = expectedHash
    )

    val result = ByteArray(data.size)
    var i = 0
    var byteRead: Byte = 0
    while (digestEnforcingStream.read().also { byteRead = it.toByte() } != -1) {
      result[i] = byteRead
      i++
    }

    assertThat(result).isEqualTo(data)
    assertThat(digestEnforcingStream.validationAttempted).isTrue()

    digestEnforcingStream.close()
  }

  @Test
  fun `success - read byte array`() {
    val data = "Hello, World! This is a longer message to test buffer reading.".toByteArray()
    val digest = MessageDigest.getInstance("SHA-256")
    val expectedHash = digest.digest(data)

    val inputStream = ByteArrayInputStream(data)
    val digestEnforcingStream = DigestValidatingInputStream(
      inputStream = inputStream,
      digest = MessageDigest.getInstance("SHA-256"),
      expectedHash = expectedHash
    )

    val result = digestEnforcingStream.readFully()

    assertThat(result.size).isEqualTo(data.size)
    assertThat(result).isEqualTo(data)
    assertThat(digestEnforcingStream.validationAttempted).isTrue()

    digestEnforcingStream.close()
  }

  @Test
  fun `success - read byte array with offset and length`() {
    val data = "This is test data for offset and length reading.".toByteArray()
    val digest = MessageDigest.getInstance("SHA-256")
    val expectedHash = digest.digest(data)

    val inputStream = ByteArrayInputStream(data)
    val digestEnforcingStream = DigestValidatingInputStream(
      inputStream = inputStream,
      digest = MessageDigest.getInstance("SHA-256"),
      expectedHash = expectedHash
    )

    val buffer = ByteArray(1024)
    var totalBytesRead = 0
    var bytesRead: Int

    while (digestEnforcingStream.read(buffer, totalBytesRead, 10).also { bytesRead = it } > 0) {
      totalBytesRead += bytesRead
    }

    val result = buffer.copyOf(totalBytesRead)
    assertThat(result).isEqualTo(data)
    assertThat(digestEnforcingStream.validationAttempted).isTrue()

    digestEnforcingStream.close()
  }

  @Test
  fun `success - empty data`() {
    val data = ByteArray(0)
    val digest = MessageDigest.getInstance("SHA-256")
    val expectedHash = digest.digest(data)

    val inputStream = ByteArrayInputStream(data)
    val digestEnforcingStream = DigestValidatingInputStream(
      inputStream = inputStream,
      digest = MessageDigest.getInstance("SHA-256"),
      expectedHash = expectedHash
    )

    // Should immediately return -1 and validate
    val endByte = digestEnforcingStream.read()
    assertThat(endByte).isEqualTo(-1)
    assertThat(digestEnforcingStream.validationAttempted).isTrue()

    digestEnforcingStream.close()
  }

  @Test
  fun `success - alternative digest, md5`() {
    val data = "Testing MD5 hash validation".toByteArray()
    val digest = MessageDigest.getInstance("MD5")
    val expectedHash = digest.digest(data)

    val inputStream = ByteArrayInputStream(data)
    val digestEnforcingStream = DigestValidatingInputStream(
      inputStream = inputStream,
      digest = MessageDigest.getInstance("MD5"),
      expectedHash = expectedHash
    )

    val result = digestEnforcingStream.readFully()

    assertThat(result).isEqualTo(data)
    assertThat(digestEnforcingStream.validationAttempted).isTrue()

    digestEnforcingStream.close()
  }

  @Test
  fun `success - multiple reads after close`() {
    val data = "Test multiple validation calls".toByteArray()
    val digest = MessageDigest.getInstance("SHA-256")
    val expectedHash = digest.digest(data)

    val inputStream = ByteArrayInputStream(data)
    val digestEnforcingStream = DigestValidatingInputStream(
      inputStream = inputStream,
      digest = MessageDigest.getInstance("SHA-256"),
      expectedHash = expectedHash
    )

    val result = digestEnforcingStream.readFully()

    // Multiple calls to read() after EOF should not cause issues
    assertThat(digestEnforcingStream.read()).isEqualTo(-1)
    assertThat(digestEnforcingStream.read()).isEqualTo(-1)
    assertThat(digestEnforcingStream.read()).isEqualTo(-1)

    assertThat(result).isEqualTo(data)
    assertThat(digestEnforcingStream.validationAttempted).isTrue()

    digestEnforcingStream.close()
  }

  @Test
  fun `failure - read byte by byte`() {
    val data = "Hello, World!".toByteArray()
    val wrongHash = ByteArray(32) // All zeros - wrong hash

    val inputStream = ByteArrayInputStream(data)
    val digestEnforcingStream = DigestValidatingInputStream(
      inputStream = inputStream,
      digest = MessageDigest.getInstance("SHA-256"),
      expectedHash = wrongHash
    )

    try {
      while (digestEnforcingStream.read() != -1) {
        // Reading byte by byte
      }

      fail("Expected InvalidCiphertextException to be thrown")
    } catch (e: InvalidMessageException) {
      // Expected exception
    } finally {
      digestEnforcingStream.close()
    }
  }

  @Test
  fun `failure - read byte array`() {
    val data = "Hello, World! This is a test message.".toByteArray()
    val wrongHash = ByteArray(32) // All zeros - wrong hash

    val inputStream = ByteArrayInputStream(data)
    val digestEnforcingStream = DigestValidatingInputStream(
      inputStream = inputStream,
      digest = MessageDigest.getInstance("SHA-256"),
      expectedHash = wrongHash
    )

    try {
      digestEnforcingStream.readFully()

      fail("Expected InvalidCiphertextException to be thrown")
    } catch (e: InvalidMessageException) {
      // Expected exception
    } finally {
      digestEnforcingStream.close()
    }
  }
}
