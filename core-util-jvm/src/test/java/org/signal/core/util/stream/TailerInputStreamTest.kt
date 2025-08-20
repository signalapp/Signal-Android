package org.signal.core.util.stream

import org.junit.Assert.assertEquals
import org.junit.Test
import org.signal.core.util.readFully

class TailerInputStreamTest {

  @Test
  fun `when I provide an incomplete stream and a known bytesLength, I can read the stream until bytesLength is reached`() {
    var currentBytesLength = 0
    val inputStream = TailerInputStream(
      streamFactory = {
        currentBytesLength += 10
        ByteArray(currentBytesLength).inputStream()
      },
      bytesLength = 50
    )

    val data = inputStream.readFully()
    assertEquals(50, data.size)
  }

  @Test
  fun `when I provide an incomplete stream and a known bytesLength, I can read the stream one byte at a time until bytesLength is reached`() {
    var currentBytesLength = 0
    val inputStream = TailerInputStream(
      streamFactory = {
        currentBytesLength += 10
        ByteArray(currentBytesLength).inputStream()
      },
      bytesLength = 20
    )

    var count = 0
    var lastRead = inputStream.read()
    while (lastRead != -1) {
      count++
      lastRead = inputStream.read()
    }

    assertEquals(20, count)
  }

  @Test
  fun `when I provide a complete stream and a known bytesLength, I can read the stream until bytesLength is reached`() {
    val inputStream = TailerInputStream(
      streamFactory = { ByteArray(50).inputStream() },
      bytesLength = 50
    )

    val data = inputStream.readFully()
    assertEquals(50, data.size)
  }

  @Test
  fun `when I provide a complete stream and a known bytesLength, I can read the stream one byte at a time until bytesLength is reached`() {
    val inputStream = TailerInputStream(
      streamFactory = { ByteArray(20).inputStream() },
      bytesLength = 20
    )

    var count = 0
    var lastRead = inputStream.read()
    while (lastRead != -1) {
      count++
      lastRead = inputStream.read()
    }

    assertEquals(20, count)
  }

  @Test
  fun `when I skip bytes, I still read until the end of bytesLength`() {
    var currentBytesLength = 0
    val inputStream = TailerInputStream(
      streamFactory = {
        currentBytesLength += 10
        ByteArray(currentBytesLength).inputStream()
      },
      bytesLength = 50
    )

    inputStream.skip(5)

    val data = inputStream.readFully()
    assertEquals(45, data.size)
  }

  @Test
  fun `when I skip more bytes than available, I can still read until the end of bytesLength`() {
    var currentBytesLength = 0
    val inputStream = TailerInputStream(
      streamFactory = {
        currentBytesLength += 10
        ByteArray(currentBytesLength).inputStream()
      },
      bytesLength = 50
    )

    inputStream.skip(15)

    val data = inputStream.readFully()
    assertEquals(40, data.size)
  }
}
