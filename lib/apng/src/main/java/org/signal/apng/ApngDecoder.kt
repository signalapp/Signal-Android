/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.apng

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.WorkerThread
import org.signal.core.util.readNBytesOrThrow
import org.signal.core.util.readUInt
import org.signal.core.util.skipNBytesOrThrow
import org.signal.core.util.stream.Crc32OutputStream
import org.signal.core.util.toUInt
import org.signal.core.util.toUShort
import org.signal.core.util.writeUInt
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * A streaming APNG decoder that only holds lightweight metadata in memory.
 *
 * During [create], the stream is scanned once to record IHDR fields, prefix chunks
 * (palette, gamma, etc.), and per-frame fcTL metadata + byte offsets into the stream
 * where image data lives. No frame image data is retained.
 *
 * At draw time, [decodeFrame] opens a stream from the factory and reads forward to the
 * requested frame's data. Since frames are almost always requested in order, each call
 * reads forward from the current position. The stream is only reopened when the animation
 * loops back to an earlier frame.
 *
 * Full spec: http://www.w3.org/TR/PNG/
 */
class ApngDecoder private constructor(
  val streamFactory: () -> InputStream,
  val metadata: Metadata,
  val frames: List<Frame>,
  private val ihdr: Chunk.IHDR,
  private val prefixChunks: List<Chunk.ArbitraryChunk>
) : Closeable {

  companion object {
    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private const val MAX_DIMENSION: UInt = 4096u

    @Throws(IOException::class)
    fun isApng(inputStream: InputStream): Boolean {
      val magic = inputStream.readNBytesOrThrow(8)
      if (!magic.contentEquals(PNG_MAGIC)) {
        return false
      }

      try {
        while (true) {
          val length: UInt = inputStream.readUInt()
          val type: String = inputStream.readNBytesOrThrow(4).toString(Charsets.US_ASCII)

          if (type == "acTL") {
            return true
          }

          if (type == "IDAT") {
            return false
          }

          // Skip over data + CRC for chunks we don't care about
          inputStream.skip(length.toLong() + 4)
        }
      } catch (e: EOFException) {
        return false
      }
    }

    /**
     * Scans the stream to build metadata, then closes it. No frame image data is retained.
     */
    @Throws(IOException::class)
    fun create(streamFactory: () -> InputStream): ApngDecoder {
      val inputStream = streamFactory()
      try {
        return scanMetadata(inputStream, streamFactory)
      } finally {
        inputStream.close()
      }
    }

    /**
     * So, PNG's are composed of chunks of various types. An APNG is a valid PNG on it's own, but has some extra
     * chunks that can be read to play the animation. The chunk structure of an APNG looks something like this:
     *
     * ---------------------
     * IHDR - Mandatory first chunk, contains metadata about the image (width, height, etc).
     *
     * [ in any order...
     *   acTL - Contains metadata about the animation. The presence of this chunk is what tells us that we have an APNG.
     *   fcTL - (Optional) If present, it tells us that the first IDAT chunk is part of the animation itself. Contains information about how the frame is
     *          rendered (see below).
     *   xxx - There are plenty of other possible chunks that can go here. We don't care about them, but we need to remember them and give them to the
     *         PNG encoder as we create each frame. Could be critical data like what palette is used for rendering.
     * ]
     *
     * IDAT - Contains the compressed image data. For an APNG, the first IDAT represents the "default" state of the image that will be shown even if the
     *        renderer doesn't support APNGs. If an fcTL is present before this, the IDAT also represents the first frame of the animation.
     *
     * ( in pairs, repeated for each frame...
     *   fcTL - This contains metadata about the frame, such as dimensions, delay, and positioning.
     *   fdAT - This contains the actual frame data that we want to render.
     * )
     *
     * xxx - There are other possible chunks that could be placed after the animation sequence but before the end of the file. Usually things like tEXt chunks
     *       that contain metadata and whatnot. They're not important.
     * IEND - Mandatory last chunk. Marks the end of the file.
     * ---------------------
     *
     * We need to read and recognize a subset of these chunks that tell us how the APNG is structured. However, the actual encoding/decoding of the PNG data
     * can be done by the system. We just need to parse out all of the frames and other metadata in order to render the animation.
     *
     * Unlike the old approach (which read all frame data into memory), this method only records byte offsets into the stream where frame data lives.
     * The actual frame data is read on demand in [decodeFrame].
     */
    private fun scanMetadata(inputStream: InputStream, streamFactory: () -> InputStream): ApngDecoder {
      val scanner = StreamScanner(inputStream)

      // Read the magic bytes to verify that this is a PNG
      val magic = scanner.readBytes(8)
      if (!magic.contentEquals(PNG_MAGIC)) {
        throw IllegalArgumentException("Not a PNG!")
      }

      // The IHDR chunk is the first chunk in a PNG file and contains metadata about the image.
      // Per spec it must appear first, so if it's missing the file is invalid.
      val ihdrLength = scanner.readUInt()
      val ihdrType = scanner.readBytes(4).toString(Charsets.US_ASCII)
      if (ihdrType != "IHDR") {
        throw IOException("First chunk is not IHDR!")
      }
      val ihdrData = scanner.readBytes(ihdrLength.toInt())
      scanner.skipBytes(4) // CRC

      val ihdr = Chunk.IHDR(
        width = ihdrData.sliceArray(0 until 4).toUInt(),
        height = ihdrData.sliceArray(4 until 8).toUInt(),
        bitDepth = ihdrData[8],
        colorType = ihdrData[9],
        compressionMethod = ihdrData[10],
        filterMethod = ihdrData[11],
        interlaceMethod = ihdrData[12]
      )

      // Next, we want to read all of the chunks up to the first IDAT chunk.
      // The first IDAT chunk represents the default image, and possibly the first frame of the animation (depending on the presence of an fcTL chunk).
      // In order for this to be a valid APNG, there _must_ be an acTL chunk before the first IDAT chunk.
      val framePrefixChunks: MutableList<Chunk.ArbitraryChunk> = mutableListOf()
      var earlyActl: Chunk.acTL? = null
      var earlyFctl: Chunk.fcTL? = null

      var chunkLength: UInt
      var chunkType: String

      while (true) {
        chunkLength = scanner.readUInt()
        chunkType = scanner.readBytes(4).toString(Charsets.US_ASCII)

        if (chunkType == "IDAT") {
          break
        }

        when (chunkType) {
          "acTL" -> {
            val data = scanner.readBytes(chunkLength.toInt())
            scanner.skipBytes(4) // CRC
            earlyActl = Chunk.acTL(
              numFrames = data.sliceArray(0 until 4).toUInt(),
              numPlays = data.sliceArray(4 until 8).toUInt()
            )
          }
          "fcTL" -> {
            val data = scanner.readBytes(chunkLength.toInt())
            scanner.skipBytes(4) // CRC
            earlyFctl = parseFctl(data)
          }
          else -> {
            val data = scanner.readBytes(chunkLength.toInt())
            val crc = scanner.readUInt()
            framePrefixChunks += Chunk.ArbitraryChunk(chunkLength, chunkType, data, crc)
          }
        }
      }

      if (earlyActl == null) {
        throw IOException("Missing acTL chunk! Not an APNG!")
      }

      val metadata = Metadata(
        width = ihdr.width.toInt(),
        height = ihdr.height.toInt(),
        numPlays = earlyActl.numPlays.toInt().takeIf { it > 0 } ?: Int.MAX_VALUE
      )

      val frames: MutableList<Frame> = mutableListOf()

      // Collect all consecutive IDAT data regions -- PNG allows splitting image data across multiple IDATs.
      // We just read the first IDAT's length + type. Data starts at the current position.
      val idatRegions = mutableListOf<DataRegion>()
      idatRegions += DataRegion(streamOffset = scanner.position, length = chunkLength.toLong())
      scanner.skipBytes(chunkLength.toLong() + 4) // data + CRC

      // Collect more consecutive IDATs
      chunkLength = scanner.readUInt()
      chunkType = scanner.readBytes(4).toString(Charsets.US_ASCII)
      while (chunkType == "IDAT") {
        idatRegions += DataRegion(streamOffset = scanner.position, length = chunkLength.toLong())
        scanner.skipBytes(chunkLength.toLong() + 4) // data + CRC
        chunkLength = scanner.readUInt()
        chunkType = scanner.readBytes(4).toString(Charsets.US_ASCII)
      }

      if (earlyFctl != null && isValidFrame(earlyFctl, ihdr)) {
        frames += Frame(fcTL = earlyFctl, dataRegions = idatRegions, isIdat = true)
      }

      // Now process remaining chunks: fcTL + fdAT pairs
      // chunkLength/chunkType already hold the first non-IDAT chunk after the IDAT run
      while (chunkType != "IEND") {
        // Scan forward to the next fcTL
        while (chunkType != "fcTL") {
          scanner.skipBytes(chunkLength.toLong() + 4) // data + CRC
          chunkLength = scanner.readUInt()
          chunkType = scanner.readBytes(4).toString(Charsets.US_ASCII)
          if (chunkType == "IEND") break
        }

        if (chunkType == "IEND") break

        // Read the fcTL data
        val fctlData = scanner.readBytes(chunkLength.toInt())
        scanner.skipBytes(4) // CRC
        val fctl = parseFctl(fctlData)

        // Collect all consecutive fdAT data regions -- frames can span multiple fdATs per the spec
        val fdatRegions = mutableListOf<DataRegion>()

        chunkLength = scanner.readUInt()
        chunkType = scanner.readBytes(4).toString(Charsets.US_ASCII)

        while (chunkType == "fdAT") {
          // fdAT data starts with 4-byte sequence number, then the actual image data
          scanner.skipBytes(4) // sequence number
          val imageDataLength = chunkLength.toLong() - 4
          fdatRegions += DataRegion(streamOffset = scanner.position, length = imageDataLength)
          scanner.skipBytes(imageDataLength + 4) // image data + CRC

          chunkLength = scanner.readUInt()
          chunkType = scanner.readBytes(4).toString(Charsets.US_ASCII)
        }

        if (fdatRegions.isNotEmpty() && isValidFrame(fctl, ihdr)) {
          frames += Frame(fcTL = fctl, dataRegions = fdatRegions, isIdat = false)
        }
      }

      return ApngDecoder(
        streamFactory = streamFactory,
        metadata = metadata,
        frames = frames,
        ihdr = ihdr,
        prefixChunks = framePrefixChunks
      )
    }

    private fun isValidFrame(fctl: Chunk.fcTL, ihdr: Chunk.IHDR): Boolean {
      return fctl.width in 1u..MAX_DIMENSION &&
        fctl.height in 1u..MAX_DIMENSION &&
        fctl.xOffset + fctl.width <= ihdr.width &&
        fctl.yOffset + fctl.height <= ihdr.height
    }

    private fun parseFctl(data: ByteArray): Chunk.fcTL {
      return Chunk.fcTL(
        sequenceNumber = data.sliceArray(0 until 4).toUInt(),
        width = data.sliceArray(4 until 8).toUInt(),
        height = data.sliceArray(8 until 12).toUInt(),
        xOffset = data.sliceArray(12 until 16).toUInt(),
        yOffset = data.sliceArray(16 until 20).toUInt(),
        delayNum = data.sliceArray(20 until 22).toUShort(),
        delayDen = data.sliceArray(22 until 24).toUShort(),
        disposeOp = when (data[24]) {
          0.toByte() -> Chunk.fcTL.DisposeOp.NONE
          1.toByte() -> Chunk.fcTL.DisposeOp.BACKGROUND
          2.toByte() -> Chunk.fcTL.DisposeOp.PREVIOUS
          else -> throw IOException("Invalid disposeOp: ${data[24]}")
        },
        blendOp = when (data[25]) {
          0.toByte() -> Chunk.fcTL.BlendOp.SOURCE
          1.toByte() -> Chunk.fcTL.BlendOp.OVER
          else -> throw IOException("Invalid blendOp: ${data[25]}")
        }
      )
    }

    private fun OutputStream.withCrc32(block: OutputStream.() -> Unit): UInt {
      return Crc32OutputStream(this)
        .apply(block)
        .currentCrc32
        .toUInt()
    }
  }

  private var currentStream: InputStream? = null
  private var currentStreamPos: Long = 0

  /**
   * Decodes the frame at the given index by streaming from the source.
   * For sequential access (the normal case), this just reads forward from the current position.
   * When looping back to an earlier frame, the stream is reopened.
   */
  @WorkerThread
  fun decodeFrame(index: Int): Bitmap {
    val frame = frames[index]
    val regions = frame.dataRegions
    val targetOffset = regions.first().streamOffset

    if (currentStream == null || currentStreamPos > targetOffset) {
      currentStream?.close()
      currentStream = streamFactory()
      currentStreamPos = 0
    }

    val stream = currentStream!!

    // Skip forward to the first data region
    val toSkip = targetOffset - currentStreamPos
    if (toSkip > 0) {
      stream.skipNBytesOrThrow(toSkip)
      currentStreamPos = targetOffset
    }

    // Read all data regions for this frame
    val totalDataSize = regions.sumOf { it.length.toInt() }
    val frameData = ByteArray(totalDataSize)
    var writeOffset = 0

    for (region in regions) {
      // Skip to this region if needed (handles gaps between consecutive chunks)
      val regionSkip = region.streamOffset - currentStreamPos
      if (regionSkip > 0) {
        stream.skipNBytesOrThrow(regionSkip)
        currentStreamPos = region.streamOffset
      }

      var read = 0
      val regionLength = region.length.toInt()
      while (read < regionLength) {
        val n = stream.read(frameData, writeOffset + read, regionLength - read)
        if (n == -1) throw IOException("Unexpected end of stream reading frame $index")
        read += n
      }
      currentStreamPos += region.length
      writeOffset += regionLength

      // Skip the CRC after this chunk's data
      stream.skipNBytesOrThrow(4)
      currentStreamPos += 4
    }

    // Encode as a standalone PNG and decode to bitmap
    val frameIhdr = if (frame.isIdat) ihdr else ihdr.copy(width = frame.fcTL.width, height = frame.fcTL.height)
    val pngData = encodePng(frameIhdr, prefixChunks, totalDataSize.toUInt(), frameData)
    return BitmapFactory.decodeByteArray(pngData, 0, pngData.size)
      ?: throw IOException("Failed to decode frame bitmap")
  }

  override fun close() {
    currentStream?.close()
    currentStream = null
  }

  private fun encodePng(ihdr: Chunk.IHDR, prefixChunks: List<Chunk.ArbitraryChunk>, dataLength: UInt, data: ByteArray): ByteArray {
    val totalPrefixSize = PNG_MAGIC.size + Chunk.IHDR.LENGTH.toInt() + prefixChunks.sumOf { it.data.size }
    val outputStream = ByteArrayOutputStream(totalPrefixSize)

    outputStream.write(PNG_MAGIC)
    outputStream.writeIHDRChunk(ihdr)
    prefixChunks.forEach { chunk ->
      outputStream.writeArbitraryChunk(chunk)
    }
    outputStream.writeIDATChunk(dataLength, data)
    outputStream.write(Chunk.IEND.data)

    return outputStream.toByteArray()
  }

  private fun OutputStream.writeIHDRChunk(ihdr: Chunk.IHDR) {
    this.writeUInt(Chunk.IHDR.LENGTH)

    val crc32: UInt = this.withCrc32 {
      write("IHDR".toByteArray(Charsets.US_ASCII))
      writeUInt(ihdr.width)
      writeUInt(ihdr.height)
      write(ihdr.bitDepth.toInt())
      write(ihdr.colorType.toInt())
      write(ihdr.compressionMethod.toInt())
      write(ihdr.filterMethod.toInt())
      write(ihdr.interlaceMethod.toInt())
    }

    this.writeUInt(crc32)
  }

  private fun OutputStream.writeIDATChunk(length: UInt, data: ByteArray) {
    this.writeUInt(length)
    val crc = this.withCrc32 {
      write("IDAT".toByteArray(Charsets.US_ASCII))
      write(data)
    }
    this.writeUInt(crc)
  }

  private fun OutputStream.writeArbitraryChunk(chunk: Chunk.ArbitraryChunk) {
    this.writeUInt(chunk.length)
    this.write(chunk.type.toByteArray(Charsets.US_ASCII))
    this.write(chunk.data)
    this.writeUInt(chunk.crc)
  }

  /**
   * Tracks position while reading through a stream during the metadata scan.
   */
  private class StreamScanner(private val inputStream: InputStream) {
    var position: Long = 0
      private set

    fun readBytes(n: Int): ByteArray {
      val bytes = inputStream.readNBytesOrThrow(n)
      position += n
      return bytes
    }

    fun readUInt(): UInt {
      return readBytes(4).toUInt()
    }

    fun skipBytes(n: Long) {
      inputStream.skipNBytesOrThrow(n)
      position += n
    }
  }

  /**
   * A region of data within the stream, identified by its byte offset and length.
   */
  class DataRegion(
    val streamOffset: Long,
    val length: Long
  )

  sealed class Chunk {
    /**
     * Contains metadata about the overall image. Must appear first.
     */
    data class IHDR(
      val width: UInt,
      val height: UInt,
      val bitDepth: Byte,
      val colorType: Byte,
      val compressionMethod: Byte,
      val filterMethod: Byte,
      val interlaceMethod: Byte
    ) : Chunk() {
      companion object {
        val LENGTH: UInt = 13.toUInt()
      }
    }

    /**
     * Marks the end of the file.
     */
    object IEND : Chunk() {
      /** Every IEND chunk has the same data. */
      val data: ByteArray = ByteArrayOutputStream().apply {
        writeUInt(0.toUInt())
        val crc: UInt = this.withCrc32 {
          write("IEND".toByteArray(Charsets.US_ASCII))
        }
        writeUInt(crc)
      }.toByteArray()
    }

    /**
     * Contains metadata about the animation. Appears before the first IDAT chunk.
     */
    class acTL(
      val numFrames: UInt,
      val numPlays: UInt
    ) : Chunk()

    /**
     * Contains metadata about a single frame of the animation. Appears before each fdAT chunk.
     */
    class fcTL(
      val sequenceNumber: UInt,
      val width: UInt,
      val height: UInt,
      val xOffset: UInt,
      val yOffset: UInt,
      val delayNum: UShort,
      val delayDen: UShort,
      val disposeOp: DisposeOp,
      val blendOp: BlendOp
    ) : Chunk() {
      /**
       * Describes how you should dispose of this frame before rendering the next one. That means that in order to render the current frame, you need to know
       * the [disposeOp] of the _previous_ frame.
       */
      enum class DisposeOp(val value: Byte) {
        /** Don't do anything. The content stays rendered. Often paired with [BlendOp.OVER] to draw "diffs" instead of whole new frames. */
        NONE(0),

        /** Replace the entire drawing surface with transparent black. */
        BACKGROUND(1),

        /** TODO still figuring this one out */
        PREVIOUS(2)
      }

      enum class BlendOp(val value: Byte) {
        /** Replace all pixels in the target region with the new pixels. */
        SOURCE(0),

        /**
         * Composites the new pixels over top of existing pixels in the region. Analogous to layering two PNGs with transparency in photoshop.
         * Often paired with [DisposeOp.NONE] to draw "diffs" instead of whole new frames.
         */
        OVER(1)
      }
    }

    /**
     * Represents a PNG chunk that we don't care about because it's not APNG-specific.
     * We still have to remember it and give it to the PNG encoder as we create each frame, but we don't need to understand it.
     */
    class ArbitraryChunk(
      val length: UInt,
      val type: String,
      val data: ByteArray,
      val crc: UInt
    ) : Chunk() {
      override fun toString(): String {
        return "Type: $type, Length: $length, CRC: $crc"
      }
    }
  }

  /**
   * Lightweight frame descriptor. Contains only the fcTL metadata and byte offsets
   * ([dataRegions]) pointing into the stream where the compressed image data lives.
   * No image data is held in memory.
   *
   * [isIdat] is true when this frame's data comes from IDAT chunks (the default image),
   * meaning the IHDR dimensions should be used as-is rather than the fcTL dimensions.
   */
  class Frame(
    val fcTL: Chunk.fcTL,
    val dataRegions: List<DataRegion>,
    val isIdat: Boolean
  )

  class Metadata(
    val width: Int,
    val height: Int,
    val numPlays: Int
  )
}
