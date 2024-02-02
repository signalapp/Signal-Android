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
import org.signal.core.util.stream.Crc32OutputStream
import org.signal.core.util.toUInt
import org.signal.core.util.toUShort
import org.signal.core.util.writeUInt
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32

/**
 * Full spec:
 * http://www.w3.org/TR/PNG/
 */
class ApngDecoder(val inputStream: InputStream) {

  companion object {
    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

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

    private fun OutputStream.withCrc32(block: OutputStream.() -> Unit): UInt {
      return Crc32OutputStream(this)
        .apply(block)
        .currentCrc32
        .toUInt()
    }
  }

  var metadata: Metadata? = null

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
   */
  fun debugGetAllFrames(): List<Frame> {
    // Read the magic bytes to verify that this is a PNG
    val magic = inputStream.readNBytesOrThrow(8)
    if (!magic.contentEquals(PNG_MAGIC)) {
      throw IllegalArgumentException("Not a PNG!")
    }

    // The IHDR chunk is the first chunk in a PNG file and contains metadata about the image.
    // Per spec it must appear first, so if it's missing the file is invalid.
    val ihdr = inputStream.readChunk() ?: throw IOException("Missing IHDR chunk!")
    if (ihdr !is Chunk.IHDR) {
      throw IOException("First chunk is not IHDR!")
    }

    // Next, we want to read all of the chunks up to the first IDAT chunk.
    // The first IDAT chunk represents the default image, and possibly the first frame the animation (depending on the presence of an fcTL chunk).
    // In order for this to be a valid APNG, there _must_ be an acTL chunk before the first IDAT chunk.
    val framePrefixChunks: MutableList<Chunk.ArbitraryChunk> = mutableListOf()
    var earlyActl: Chunk.acTL? = null
    var earlyFctl: Chunk.fcTL? = null

    var chunk = inputStream.readChunk()
    while (chunk != null && chunk !is Chunk.IDAT) {
      when (chunk) {
        is Chunk.acTL -> earlyActl = chunk
        is Chunk.fcTL -> earlyFctl = chunk
        is Chunk.ArbitraryChunk -> framePrefixChunks += chunk
        else -> throw IOException("Unexpected chunk type before IDAT: $chunk")
      }
      chunk = inputStream.readChunk()
    }

    if (chunk == null) {
      throw EOFException("Hit the end of the file before we hit an IDAT!")
    }

    if (earlyActl == null) {
      throw IOException("Missing acTL chunk! Not an APNG!")
    }

    metadata = Metadata(
      width = ihdr.width.toInt(),
      height = ihdr.height.toInt(),
      numPlays = earlyActl.numPlays.toInt().takeIf { it > 0 } ?: Int.MAX_VALUE
    )

    // Collect all consecutive IDAT chunks -- PNG allows splitting image data across multiple IDATs
    val idatData = ByteArrayOutputStream()
    idatData.write((chunk as Chunk.IDAT).data)
    chunk = inputStream.readChunk()
    while (chunk is Chunk.IDAT) {
      idatData.write(chunk.data)
      chunk = inputStream.readChunk()
    }

    val frames: MutableList<Frame> = mutableListOf()

    if (earlyFctl != null) {
      val allIdatData = idatData.toByteArray()
      val pngData = encodePng(ihdr, framePrefixChunks, allIdatData.size.toUInt(), allIdatData)
      frames += Frame(pngData, earlyFctl)
    }

    // chunk already points to the first non-IDAT chunk from the collection loop above
    while (chunk != null && chunk !is Chunk.IEND) {
      while (chunk != null && chunk !is Chunk.fcTL) {
        chunk = inputStream.readChunk()
      }

      if (chunk == null) {
        break
      }

      if (chunk !is Chunk.fcTL) {
        throw IOException("Expected an fcTL chunk, got $chunk instead!")
      }
      val fctl: Chunk.fcTL = chunk

      chunk = inputStream.readChunk()

      if (chunk !is Chunk.fdAT) {
        throw IOException("Expected an fdAT chunk, got $chunk instead!")
      }

      // Collect all consecutive fdAT chunks -- frames can span multiple fdATs per the spec
      val fdatData = ByteArrayOutputStream()
      while (chunk is Chunk.fdAT) {
        fdatData.write(chunk.data)
        chunk = inputStream.readChunk()
      }
      val allFdatData = fdatData.toByteArray()

      val pngData = encodePng(ihdr.copy(width = fctl.width, height = fctl.height), framePrefixChunks, allFdatData.size.toUInt(), allFdatData)
      frames += Frame(pngData, fctl)
    }

    return frames
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

  // TODO private
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
     * Contains the actual compressed PNG image data. For an APNG, the IDAT chunk represents the default image and possibly the first frame of the animation.
     */
    class IDAT(val length: UInt, val data: ByteArray) : Chunk()

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
     * Contains the actual compressed image data for a single frame of the animation. Appears after each fcTL chunk.
     * The contents of [data] are actually an [IDAT] chunk, meaning that to decode the frame, we can just bolt metadata to the front of the file and hand
     * it off to the system decoder.
     */
    class fdAT(
      val length: UInt,
      val sequenceNumber: UInt,
      val data: ByteArray
    ) : Chunk()

    /**
     * Represents a PNG chunk that we don't care about because it's not APNG-specific.
     * We still have to remember it and give it the PNG encoder as we create each frame, but we don't need to understand it.
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

  class Frame(
    val pngData: ByteArray,
    val fcTL: Chunk.fcTL
  ) {
    @WorkerThread
    fun decodeBitmap(): Bitmap {
      return BitmapFactory.decodeByteArray(pngData, 0, pngData.size)
        ?: throw IOException("Failed to decode frame bitmap")
    }
  }

  class Metadata(
    val width: Int,
    val height: Int,
    val numPlays: Int
  )
}

private fun InputStream.readChunk(): ApngDecoder.Chunk? {
  try {
    val length: UInt = this.readUInt()
    val type: String = this.readNBytesOrThrow(4).toString(Charsets.US_ASCII)
    val data = this.readNBytesOrThrow(length.toInt())
    val dataCrc = CRC32().also { it.update(type.toByteArray(Charsets.US_ASCII)) }.also { it.update(data) }.value
    val targetCrc = this.readUInt().toLong()

    if (dataCrc != targetCrc) {
      return null
    }

    return when (type) {
      "IHDR" -> {
        ApngDecoder.Chunk.IHDR(
          width = data.sliceArray(0 until 4).toUInt(),
          height = data.sliceArray(4 until 8).toUInt(),
          bitDepth = data[8],
          colorType = data[9],
          compressionMethod = data[10],
          filterMethod = data[11],
          interlaceMethod = data[12]
        )
      }

      "IDAT" -> {
        ApngDecoder.Chunk.IDAT(length, data)
      }

      "IEND" -> {
        ApngDecoder.Chunk.IEND
      }

      "acTL" -> {
        ApngDecoder.Chunk.acTL(
          numFrames = data.sliceArray(0 until 4).toUInt(),
          numPlays = data.sliceArray(4 until 8).toUInt()
        )
      }

      "fcTL" -> {
        ApngDecoder.Chunk.fcTL(
          sequenceNumber = data.sliceArray(0 until 4).toUInt(),
          width = data.sliceArray(4 until 8).toUInt(),
          height = data.sliceArray(8 until 12).toUInt(),
          xOffset = data.sliceArray(12 until 16).toUInt(),
          yOffset = data.sliceArray(16 until 20).toUInt(),
          delayNum = data.sliceArray(20 until 22).toUShort(),
          delayDen = data.sliceArray(22 until 24).toUShort(),
          disposeOp = when (data[24]) {
            0.toByte() -> ApngDecoder.Chunk.fcTL.DisposeOp.NONE
            1.toByte() -> ApngDecoder.Chunk.fcTL.DisposeOp.BACKGROUND
            2.toByte() -> ApngDecoder.Chunk.fcTL.DisposeOp.PREVIOUS
            else -> throw IOException("Invalid disposeOp: ${data[24]}")
          },
          blendOp = when (data[25]) {
            0.toByte() -> ApngDecoder.Chunk.fcTL.BlendOp.SOURCE
            1.toByte() -> ApngDecoder.Chunk.fcTL.BlendOp.OVER
            else -> throw IOException("Invalid blendOp: ${data[25]}")
          }
        )
      }

      "fdAT" -> {
        ApngDecoder.Chunk.fdAT(
          length = length,
          sequenceNumber = data.sliceArray(0 until 4).toUInt(),
          data = data.sliceArray(4 until data.size)
        )
      }

      else -> {
        ApngDecoder.Chunk.ArbitraryChunk(length, type, data, targetCrc.toInt().toUInt())
      }
    }
  } catch (e: EOFException) {
    return null
  }
}
