/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

class ImageAlphaSnifferTest {

  @Test
  fun `png with rgb alpha color type has alpha`() {
    assertTrue(sniff(png(colorType = PNG_RGBA)))
  }

  @Test
  fun `png with grayscale alpha color type has alpha`() {
    assertTrue(sniff(png(colorType = PNG_GRAY_ALPHA)))
  }

  @Test
  fun `opaque rgb png has no alpha`() {
    assertFalse(sniff(png(colorType = PNG_RGB)))
  }

  @Test
  fun `opaque grayscale png has no alpha`() {
    assertFalse(sniff(png(colorType = PNG_GRAY)))
  }

  @Test
  fun `palette png without tRNS has no alpha`() {
    assertFalse(sniff(png(PNG_PALETTE, "PLTE" to ByteArray(6))))
  }

  @Test
  fun `palette png with tRNS has alpha`() {
    assertTrue(sniff(png(PNG_PALETTE, "PLTE" to ByteArray(6), "tRNS" to ByteArray(2))))
  }

  @Test
  fun `rgb png with tRNS has alpha`() {
    assertTrue(sniff(png(PNG_RGB, "gAMA" to ByteArray(4), "tRNS" to ByteArray(6))))
  }

  @Test
  fun `tRNS behind a large metadata chunk is still found`() {
    assertTrue(sniff(png(PNG_RGB, "iTXt" to ByteArray(60_000), "tRNS" to ByteArray(6))))
  }

  @Test
  fun `tRNS beyond the scan limit is treated as opaque`() {
    assertFalse(sniff(png(PNG_RGB, "iTXt" to ByteArray(70_000), "tRNS" to ByteArray(6))))
  }

  @Test
  fun `png whose first chunk is not IHDR is treated as opaque`() {
    val bytes = png(colorType = PNG_RGBA)
    "sRGB".toByteArray(Charsets.US_ASCII).copyInto(bytes, destinationOffset = 12)
    assertFalse(sniff(bytes))
  }

  @Test
  fun `truncated png is treated as opaque`() {
    assertFalse(sniff(png(colorType = PNG_RGBA).copyOf(20)))
  }

  @Test
  fun `png written by ImageIO with alpha has alpha`() {
    assertTrue(sniff(imageIoPng(BufferedImage.TYPE_INT_ARGB)))
  }

  @Test
  fun `png written by ImageIO without alpha has no alpha`() {
    assertFalse(sniff(imageIoPng(BufferedImage.TYPE_INT_RGB)))
  }

  @Test
  fun `extended webp with alpha flag has alpha`() {
    assertTrue(sniff(webpVp8x(flags = 0x10)))
  }

  @Test
  fun `extended webp with alpha and animation flags has alpha`() {
    assertTrue(sniff(webpVp8x(flags = 0x12)))
  }

  @Test
  fun `extended webp without alpha flag has no alpha`() {
    assertFalse(sniff(webpVp8x(flags = 0x20)))
  }

  @Test
  fun `lossless webp with alpha bit has alpha`() {
    assertTrue(sniff(webpVp8l(alpha = true)))
  }

  @Test
  fun `lossless webp without alpha bit has no alpha`() {
    assertFalse(sniff(webpVp8l(alpha = false)))
  }

  @Test
  fun `lossless webp with wrong signature byte is treated as opaque`() {
    val bytes = webpVp8l(alpha = true)
    bytes[20] = 0x00
    assertFalse(sniff(bytes))
  }

  @Test
  fun `simple lossy webp has no alpha`() {
    assertFalse(sniff(webp("VP8 ", ByteArray(10))))
  }

  @Test
  fun `jpeg is treated as opaque`() {
    assertFalse(sniff(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(32)))
  }

  @Test
  fun `empty stream is treated as opaque`() {
    assertFalse(sniff(ByteArray(0)))
  }

  @Test
  fun `stream is closed after sniffing`() {
    var closed = false
    val stream = object : ByteArrayInputStream(webpVp8x(flags = 0x10)) {
      override fun close() {
        closed = true
        super.close()
      }
    }

    ImageAlphaSniffer.hasAlpha(stream)

    assertTrue(closed)
  }

  private fun sniff(bytes: ByteArray): Boolean {
    return ImageAlphaSniffer.hasAlpha(ByteArrayInputStream(bytes))
  }

  /** A structurally valid PNG with a 16x16 IHDR, the given chunks, an IDAT and IEND. CRCs are zero, the sniffer does not check them. */
  private fun png(colorType: Int, vararg chunks: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
    val ihdr = ByteBuffer.allocate(13)
      .putInt(16)
      .putInt(16)
      .put(8)
      .put(colorType.toByte())
      .put(0)
      .put(0)
      .put(0)
      .array()
    out.writeChunk("IHDR", ihdr)
    chunks.forEach { (type, data) -> out.writeChunk(type, data) }
    out.writeChunk("IDAT", ByteArray(4))
    out.writeChunk("IEND", ByteArray(0))
    return out.toByteArray()
  }

  private fun ByteArrayOutputStream.writeChunk(type: String, data: ByteArray) {
    write(ByteBuffer.allocate(4).putInt(data.size).array())
    write(type.toByteArray(Charsets.US_ASCII))
    write(data)
    write(ByteArray(4))
  }

  private fun imageIoPng(imageType: Int): ByteArray {
    val out = ByteArrayOutputStream()
    assertTrue(ImageIO.write(BufferedImage(32, 32, imageType), "png", out))
    return out.toByteArray()
  }

  /** A RIFF/WEBP container holding a single chunk. */
  private fun webp(fourcc: String, payload: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    out.write("RIFF".toByteArray(Charsets.US_ASCII))
    out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(4 + 8 + payload.size).array())
    out.write("WEBP".toByteArray(Charsets.US_ASCII))
    out.write(fourcc.toByteArray(Charsets.US_ASCII))
    out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.size).array())
    out.write(payload)
    return out.toByteArray()
  }

  /** VP8X payload: one flags byte, three reserved bytes, 24-bit canvas width minus one, 24-bit canvas height minus one. */
  private fun webpVp8x(flags: Int): ByteArray {
    return webp("VP8X", byteArrayOf(flags.toByte(), 0, 0, 0, 15, 0, 0, 15, 0, 0))
  }

  /** VP8L payload: signature byte, then 14 bits width minus one, 14 bits height minus one, 1 bit alpha_is_used, 3 bits version, little-endian. */
  private fun webpVp8l(alpha: Boolean): ByteArray {
    var header = 15 or (15 shl 14)
    if (alpha) {
      header = header or (1 shl 28)
    }
    val payload = ByteBuffer.allocate(5)
      .order(ByteOrder.LITTLE_ENDIAN)
      .put(0x2F)
      .putInt(header)
      .array()
    return webp("VP8L", payload)
  }

  companion object {
    private const val PNG_GRAY = 0
    private const val PNG_RGB = 2
    private const val PNG_PALETTE = 3
    private const val PNG_GRAY_ALPHA = 4
    private const val PNG_RGBA = 6
  }
}
