package org.whispersystems.signalservice.api.crypto

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import assertk.fail
import org.junit.Test
import org.signal.core.util.kibiBytes
import org.signal.core.util.mebiBytes
import org.signal.core.util.readFully
import org.signal.libsignal.protocol.InvalidMessageException
import org.whispersystems.signalservice.internal.util.Util
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class MacValidatingInputStreamTest {

  @Test
  fun `success - simple byte array read`() {
    val data = "Hello, World!".toByteArray()
    val key = Util.getSecretBytes(32)
    val dataWithMac = createDataWithMac(data, key)

    val inputStream = ByteArrayInputStream(dataWithMac)
    val mac = createMac(key)
    val macValidatingStream = MacValidatingInputStream(inputStream, mac)

    val result = macValidatingStream.readFully()

    assertThat(result).isEqualTo(dataWithMac)
    macValidatingStream.close()
  }

  @Test
  fun `success - byte by byte`() {
    val data = "Hello, World!".toByteArray()
    val key = Util.getSecretBytes(32)
    val dataWithMac = createDataWithMac(data, key)

    val inputStream = ByteArrayInputStream(dataWithMac)
    val mac = createMac(key)
    val macValidatingStream = MacValidatingInputStream(inputStream, mac)

    val out = ByteArrayOutputStream()
    var read = -1
    while (macValidatingStream.read().also { read = it } != -1) {
      out.write(read)
    }
    val result = out.toByteArray()

    assertThat(result).isEqualTo(dataWithMac)
    macValidatingStream.close()
  }

  @Test
  fun `success - many different sizes`() {
    for (i in 1..100) {
      val data = Util.getSecretBytes(Random.nextLong(from = 256.kibiBytes.bytes, until = 2.mebiBytes.bytes).toInt())
      val key = Util.getSecretBytes(32)
      val dataWithMac = createDataWithMac(data, key)

      val inputStream = ByteArrayInputStream(dataWithMac)
      val mac = createMac(key)
      val macValidatingStream = MacValidatingInputStream(inputStream, mac)

      val result = macValidatingStream.readFully()

      assertThat(result).isEqualTo(dataWithMac)
      assertThat(macValidatingStream.validationAttempted).isTrue()
      macValidatingStream.close()
    }
  }

  @Test
  fun `success - empty data`() {
    val data = ByteArray(0)
    val key = Util.getSecretBytes(32)
    val dataWithMac = createDataWithMac(data, key)

    val inputStream = ByteArrayInputStream(dataWithMac)
    val mac = createMac(key)
    val macValidatingStream = MacValidatingInputStream(inputStream, mac)

    val result = macValidatingStream.readFully()

    assertThat(result).isEqualTo(dataWithMac)
    assertThat(macValidatingStream.validationAttempted).isTrue()
    macValidatingStream.close()
  }

  @Test
  fun `success - data exactly MAC length`() {
    val key = Util.getSecretBytes(32)
    val mac = createMac(key)
    val macLength = mac.macLength
    val data = ByteArray(macLength) { (it % 256).toByte() } // Data same size as MAC
    val dataWithMac = createDataWithMac(data, key)

    val inputStream = ByteArrayInputStream(dataWithMac)
    val mac2 = createMac(key)
    val macValidatingStream = MacValidatingInputStream(inputStream, mac2)

    val result = macValidatingStream.readFully()

    assertThat(result).isEqualTo(dataWithMac)
    assertThat(macValidatingStream.validationAttempted).isTrue()
    macValidatingStream.close()
  }

  @Test
  fun `success - multiple reads after end of stream`() {
    val data = "Test multiple reads after EOF".toByteArray()
    val key = Util.getSecretBytes(32)
    val dataWithMac = createDataWithMac(data, key)

    val inputStream = ByteArrayInputStream(dataWithMac)
    val mac = createMac(key)
    val macValidatingStream = MacValidatingInputStream(inputStream, mac)

    val result = macValidatingStream.readFully()

    // Multiple calls to read() after EOF should return -1
    assertThat(macValidatingStream.read()).isEqualTo(-1)
    assertThat(macValidatingStream.read()).isEqualTo(-1)
    assertThat(macValidatingStream.read()).isEqualTo(-1)

    assertThat(result).isEqualTo(dataWithMac)
    assertThat(macValidatingStream.validationAttempted).isTrue()
    macValidatingStream.close()
  }

  @Test
  fun `failure - invalid MAC`() {
    val data = "Hello, World!".toByteArray()
    val key = Util.getSecretBytes(32)
    val wrongKey = ByteArray(32) { 24 }
    val dataWithMac = createDataWithMac(data, key)

    val inputStream = ByteArrayInputStream(dataWithMac)
    val mac = createMac(wrongKey) // Wrong key
    val macValidatingStream = MacValidatingInputStream(inputStream, mac)

    try {
      macValidatingStream.readFully()
      fail("Expected InvalidMessageException to be thrown")
    } catch (e: InvalidMessageException) {
      assertThat(e.message).isEqualTo("MAC validation failed!")
    } finally {
      macValidatingStream.close()
    }
  }

  @Test
  fun `failure - insufficient data for MAC`() {
    val key = Util.getSecretBytes(32)
    val mac = createMac(key)
    val macLength = mac.macLength
    val insufficientData = ByteArray(macLength - 1) { 5 } // Less than MAC length

    val inputStream = ByteArrayInputStream(insufficientData)
    val mac2 = createMac(key)
    val macValidatingStream = MacValidatingInputStream(inputStream, mac2)

    try {
      macValidatingStream.readFully()
      fail("Expected InvalidMessageException to be thrown")
    } catch (e: InvalidMessageException) {
      assertThat(e.message).isEqualTo("Stream ended before MAC could be read. Expected $macLength bytes, got ${insufficientData.size}")
    } finally {
      macValidatingStream.close()
    }
  }

  private fun createMac(key: ByteArray): Mac {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac
  }

  private fun createDataWithMac(data: ByteArray, key: ByteArray): ByteArray {
    val mac = createMac(key)
    val macBytes = mac.doFinal(data)
    return data + macBytes
  }
}
