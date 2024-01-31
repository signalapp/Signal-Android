package org.thoughtcrime.securesms.video.videoconverter.mediadatasource

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.signal.core.util.skipNBytesCompat
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Arrays
import kotlin.random.Random

@OptIn(ExperimentalStdlibApi::class)
class InputStreamMediaDataSourceTest {
  companion object {
    const val BUFFER_SIZE = 1024
    const val DATA_SIZE = 8192
  }
  private lateinit var dataSource: TestInputStreamMediaDataSource
  private val outputBuffer = ByteArray(BUFFER_SIZE)

  @Before
  fun setUp() {
    dataSource = TestInputStreamMediaDataSource(Random.Default.nextBytes(DATA_SIZE))
    Arrays.fill(outputBuffer, 0)
  }

  /**
   * Happy path test for reading from the start of the stream.
   */
  @Test
  fun testStartRead() {
    val readLength = BUFFER_SIZE
    dataSource.readAt(0, outputBuffer, 0, readLength)
    assertArrayEquals(dataSource.getSliceOfData(0..<readLength), outputBuffer)
  }

  /**
   * Make sure that reading from a specified index works.
   */
  @Test
  fun testSkipForward() {
    val readLength = BUFFER_SIZE
    val skipOffset = BUFFER_SIZE
    val endIndex = skipOffset + readLength
    dataSource.readAt(skipOffset.toLong(), outputBuffer, 0, readLength)
    assertArrayEquals(dataSource.getSliceOfData(skipOffset..<endIndex), outputBuffer)
  }

  /**
   * "Skipping backwards" actually involves recreating the underlying stream and skipping forwards. This tests that.
   */
  @Test
  fun testSkipBackward() {
    val readLength = BUFFER_SIZE
    val skipOffset = BUFFER_SIZE
    val skipAheadAmount = skipOffset * 2
    val endIndex = skipOffset + readLength
    dataSource.readAt(skipAheadAmount.toLong(), outputBuffer, 0, readLength)

    dataSource.readAt(skipOffset.toLong(), outputBuffer, 0, readLength)
    assertArrayEquals(dataSource.getSliceOfData(skipOffset..<endIndex), outputBuffer)
  }

  /**
   * Successfully read the final n bytes of a stream, even though >n were requested
   */
  @Test
  fun testReadPastInputStreamSize() {
    val readLength = 512
    val distanceFromEnd = readLength / 2
    val skipOffset = DATA_SIZE - distanceFromEnd
    val readResult = dataSource.readAt(skipOffset.toLong(), outputBuffer, 0, readLength)

    assertEquals(distanceFromEnd, readResult)
    assertArrayEquals(dataSource.getSliceOfData(skipOffset..<DATA_SIZE), outputBuffer.sliceArray(0..<distanceFromEnd))
  }

  /**
   * Successfully read the final n bytes of a stream, even though >n were requested
   */
  @Test
  fun testReadUpToEndAndThenKeepReading() {
    val readLength = 512
    val distanceFromEnd = readLength / 2
    val skipOffset = DATA_SIZE - distanceFromEnd

    val readResultLastOfStream = dataSource.readAt(skipOffset.toLong(), outputBuffer, 0, readLength)
    val readResultAtEndOfStream = dataSource.readAt((skipOffset + readResultLastOfStream).toLong(), outputBuffer, 0, readLength)

    assertEquals(-1, readResultAtEndOfStream)
    assertArrayEquals(dataSource.getSliceOfData(skipOffset..<DATA_SIZE), outputBuffer.sliceArray(0..<distanceFromEnd))
  }

  /**
   * A negative position is outside the stream, should return EOS.
   */
  @Test
  fun testReadNegativePosition() {
    val readResult = dataSource.readAt(-128, outputBuffer, 0, BUFFER_SIZE)

    assertEquals(-1, readResult)
    assertArrayEquals(ByteArray(BUFFER_SIZE), outputBuffer)
  }

  private class TestInputStreamMediaDataSource(private val data: ByteArray) : InputStreamMediaDataSource() {
    override fun getSize() = data.size.toLong()

    override fun createInputStream(position: Long): InputStream {
      val inputStream = ByteArrayInputStream(data)
      inputStream.skipNBytesCompat(position)
      return inputStream
    }

    fun getSliceOfData(indices: IntRange): ByteArray {
      return data.sliceArray(indices)
    }
  }
}
