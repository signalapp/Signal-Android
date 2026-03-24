/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.stream

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test
import org.signal.core.util.readFully
import kotlin.math.min
import kotlin.random.Random

class TrimmingInputStreamTest {

  @Test
  fun `when I fully read the stream via a buffer, I should exclude the last trimSize bytes`() {
    val initialData = testData(100)
    val inputStream = TrimmingInputStream(initialData.inputStream(), trimSize = 25)
    val data = inputStream.readFully()

    assertThat(data.size).isEqualTo(75)
    assertThat(data).isEqualTo(initialData.copyOfRange(0, 75))
  }

  @Test
  fun `when I fully read the stream via a buffer, I should exclude the last trimSize bytes - many sizes`() {
    for (i in 1..100) {
      val arraySize = Random.nextInt(1024, 2 * 1024 * 1024)
      val trimSize = min(arraySize, Random.nextInt(1024))

      val initialData = testData(arraySize)
      val innerStream = initialData.inputStream()
      val inputStream = TrimmingInputStream(innerStream, trimSize = trimSize)
      val data = inputStream.readFully()

      assertThat(data.size).isEqualTo(arraySize - trimSize)
      assertThat(data).isEqualTo(initialData.copyOfRange(0, arraySize - trimSize))
    }
  }

  @Test
  fun `when I fully read the stream via a buffer with drain set, I should exclude the last trimSize bytes but still drain the remaining stream - many sizes`() {
    for (i in 1..100) {
      val arraySize = Random.nextInt(1024, 2 * 1024 * 1024)
      val trimSize = min(arraySize, Random.nextInt(1024))

      val initialData = testData(arraySize)
      val innerStream = initialData.inputStream()
      val inputStream = TrimmingInputStream(innerStream, trimSize = trimSize, drain = true)
      val data = inputStream.readFully()

      assertThat(data.size).isEqualTo(arraySize - trimSize)
      assertThat(data).isEqualTo(initialData.copyOfRange(0, arraySize - trimSize))
      assertThat(innerStream.available()).isEqualTo(0)
    }
  }

  @Test
  fun `when I fully read the stream and the trimSize is greater than the stream length, I should get zero bytes`() {
    val initialData = testData(100)
    val inputStream = TrimmingInputStream(initialData.inputStream(), trimSize = 200)
    val data = inputStream.readFully()

    assertThat(data.size).isEqualTo(0)
  }

  @Test
  fun `when I fully read the stream via a buffer with no trimSize, I should get all bytes`() {
    val inputStream = TrimmingInputStream(ByteArray(100).inputStream(), trimSize = 0)
    val data = inputStream.readFully()

    assertThat(data.size).isEqualTo(100)
  }

  @Test
  fun `when I fully read the stream one byte at a time, I should exclude the last trimSize bytes`() {
    val inputStream = TrimmingInputStream(ByteArray(100).inputStream(), trimSize = 25)

    var count = 0
    var lastRead = inputStream.read()
    while (lastRead != -1) {
      count++
      lastRead = inputStream.read()
    }

    assertThat(count).isEqualTo(75)
  }

  @Test
  fun `when I fully read the stream one byte at a time with no trimSize, I should get all bytes`() {
    val inputStream = TrimmingInputStream(ByteArray(100).inputStream(), trimSize = 0)

    var count = 0
    var lastRead = inputStream.read()
    while (lastRead != -1) {
      count++
      lastRead = inputStream.read()
    }

    assertThat(count).isEqualTo(100)
  }

  @Test
  fun `when I skip past the the trimSize, I should get -1`() {
    val inputStream = TrimmingInputStream(ByteArray(100).inputStream(), trimSize = 25)

    val skipCount = inputStream.skip(100)
    val read = inputStream.read()

    assertThat(skipCount).isEqualTo(75)
    assertThat(read).isEqualTo(-1)
  }

  @Test
  fun `when I skip, I should still truncate correctly afterwards`() {
    val inputStream = TrimmingInputStream(ByteArray(100).inputStream(), trimSize = 25)

    val skipCount = inputStream.skip(50)
    val data = inputStream.readFully()

    assertThat(skipCount).isEqualTo(50)
    assertThat(data.size).isEqualTo(25)
  }

  @Test
  fun `when I skip more than the remaining bytes, I still respect trimSize`() {
    val initialData = testData(100)
    val inputStream = TrimmingInputStream(initialData.inputStream(), trimSize = 25)

    val skipCount = inputStream.skip(100)

    assertThat(skipCount).isEqualTo(75)
  }

  private fun testData(length: Int): ByteArray {
    return ByteArray(length) { (it % 0xFF).toByte() }
  }
}
