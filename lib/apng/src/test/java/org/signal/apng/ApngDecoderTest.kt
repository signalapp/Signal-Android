/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.apng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
class ApngDecoderTest {

  // -- isApng --

  @Test
  fun `isApng returns false for static PNG`() {
    assertFalse(ApngDecoder.isApng(open("test00.png")))
  }

  @Test
  fun `isApng returns true for single-frame APNG using default image`() {
    assertTrue(ApngDecoder.isApng(open("test01.png")))
  }

  @Test
  fun `isApng returns true for single-frame APNG ignoring default image`() {
    assertTrue(ApngDecoder.isApng(open("test02.png")))
  }

  @Test
  fun `isApng returns true for multi-frame APNG`() {
    assertTrue(ApngDecoder.isApng(open("test07.png")))
  }

  @Test
  fun `isApng returns true for real-world image`() {
    assertTrue(ApngDecoder.isApng(open("ball.png")))
  }

  // -- Basic parsing --

  @Test
  fun `test01 - single frame using default image`() {
    val result = decode("test01.png")
    assertNotNull(result.metadata)
    assertEquals(1, result.frames.size)
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test02 - single frame ignoring default image`() {
    val result = decode("test02.png")
    assertNotNull(result.metadata)
    assertEquals(1, result.frames.size)
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  // -- Split IDAT and fdAT --

  @Test
  fun `test03 - split IDAT is not APNG`() {
    assertFalse(ApngDecoder.isApng(open("test03.png")))
  }

  @Test
  fun `test04 - split IDAT with zero-length chunk is not APNG`() {
    assertFalse(ApngDecoder.isApng(open("test04.png")))
  }

  @Test
  fun `test05 - split fdAT parses successfully`() {
    val result = decode("test05.png")
    assertNotNull(result.metadata)
    assertEquals(1, result.frames.size)
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test06 - split fdAT with zero-length chunk parses successfully`() {
    val result = decode("test06.png")
    assertNotNull(result.metadata)
    assertEquals(1, result.frames.size)
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  // -- Dispose ops --

  @Test
  fun `test07 - DISPOSE_OP_NONE basic`() {
    val result = decode("test07.png")
    assertTrue(result.frames.size >= 2)
    assertTrue(result.frames.any { it.fcTL.disposeOp == ApngDecoder.Chunk.fcTL.DisposeOp.NONE })
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test08 - DISPOSE_OP_BACKGROUND basic`() {
    val result = decode("test08.png")
    assertTrue(result.frames.size >= 2)
    assertTrue(result.frames.any { it.fcTL.disposeOp == ApngDecoder.Chunk.fcTL.DisposeOp.BACKGROUND })
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test09 - DISPOSE_OP_BACKGROUND final frame`() {
    val result = decode("test09.png")
    assertTrue(result.frames.size >= 2)
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test10 - DISPOSE_OP_PREVIOUS basic`() {
    val result = decode("test10.png")
    assertTrue(result.frames.size >= 2)
    assertTrue(result.frames.any { it.fcTL.disposeOp == ApngDecoder.Chunk.fcTL.DisposeOp.PREVIOUS })
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test11 - DISPOSE_OP_PREVIOUS final frame`() {
    val result = decode("test11.png")
    assertTrue(result.frames.size >= 2)
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test12 - DISPOSE_OP_PREVIOUS first frame`() {
    val result = decode("test12.png")
    assertTrue(result.frames.size >= 2)
    assertEquals(ApngDecoder.Chunk.fcTL.DisposeOp.PREVIOUS, result.frames[0].fcTL.disposeOp)
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  // -- Dispose ops with regions --

  @Test
  fun `test13 - DISPOSE_OP_NONE in region`() {
    val result = decode("test13.png")
    assertTrue(result.frames.size >= 2)
    val subFrame = result.frames.find { it.fcTL.width != result.metadata!!.width.toUInt() || it.fcTL.height != result.metadata!!.height.toUInt() }
    assertNotNull("Expected at least one sub-region frame", subFrame)
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test14 - DISPOSE_OP_BACKGROUND before region`() {
    val result = decode("test14.png")
    assertTrue(result.frames.size >= 2)
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test15 - DISPOSE_OP_BACKGROUND in region`() {
    val result = decode("test15.png")
    assertTrue(result.frames.size >= 2)
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test16 - DISPOSE_OP_PREVIOUS in region`() {
    val result = decode("test16.png")
    assertTrue(result.frames.size >= 2)
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  // -- Blend ops --

  @Test
  fun `test17 - BLEND_OP_SOURCE on solid colour`() {
    val result = decode("test17.png")
    assertTrue(result.frames.size >= 2)
    assertTrue(result.frames.any { it.fcTL.blendOp == ApngDecoder.Chunk.fcTL.BlendOp.SOURCE })
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test18 - BLEND_OP_SOURCE on transparent colour`() {
    val result = decode("test18.png")
    assertTrue(result.frames.size >= 2)
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test19 - BLEND_OP_SOURCE on nearly-transparent colour`() {
    val result = decode("test19.png")
    assertTrue(result.frames.size >= 2)
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test20 - BLEND_OP_OVER on solid and transparent colours`() {
    val result = decode("test20.png")
    assertTrue(result.frames.size >= 2)
    assertTrue(result.frames.any { it.fcTL.blendOp == ApngDecoder.Chunk.fcTL.BlendOp.OVER })
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test21 - BLEND_OP_OVER repeatedly with nearly-transparent colours`() {
    val result = decode("test21.png")
    assertTrue(result.frames.size >= 2)
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  // -- Blending and gamma --

  @Test
  fun `test22 - BLEND_OP_OVER with gamma`() {
    val result = decode("test22.png")
    assertTrue(result.frames.isNotEmpty())
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test23 - BLEND_OP_OVER with gamma nearly black`() {
    val result = decode("test23.png")
    assertTrue(result.frames.isNotEmpty())
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  // -- Chunk ordering --

  @Test
  fun `test24 - fcTL before acTL parses successfully`() {
    val result = decode("test24.png")
    assertNotNull(result.metadata)
    assertTrue(result.frames.isNotEmpty())
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  // -- Delays --

  @Test
  fun `test25 - basic delays`() {
    val result = decode("test25.png")
    assertTrue(result.frames.size >= 2)

    val delaysMs = result.frames.map { it.delayMs }
    assertTrue("Expected at least one 500ms delay", delaysMs.any { it == 500L })
    assertTrue("Expected at least one 1000ms delay", delaysMs.any { it == 1000L })
  }

  @Test
  fun `test26 - rounding of division`() {
    val result = decode("test26.png")
    assertTrue(result.frames.size >= 2)

    val delaysMs = result.frames.map { it.delayMs }
    assertTrue("Expected at least one 500ms delay", delaysMs.any { it == 500L })
    assertTrue("Expected at least one 1000ms delay", delaysMs.any { it == 1000L })
  }

  @Test
  fun `test27 - 16-bit numerator and denominator`() {
    val result = decode("test27.png")
    assertTrue(result.frames.size >= 2)

    val delaysMs = result.frames.map { it.delayMs }
    assertTrue("Expected at least one 500ms delay", delaysMs.any { it == 500L })
    assertTrue("Expected at least one 1000ms delay", delaysMs.any { it == 1000L })
  }

  @Test
  fun `test28 - zero denominator treated as 100`() {
    val result = decode("test28.png")
    assertTrue(result.frames.size >= 2)

    val delaysMs = result.frames.map { it.delayMs }
    assertTrue("Expected at least one 500ms delay", delaysMs.any { it == 500L })
    assertTrue("Expected at least one 1000ms delay", delaysMs.any { it == 1000L })

    result.frames.forEach {
      val den = it.fcTL.delayDen.toInt()
      if (den == 0) {
        val expectedMs = it.fcTL.delayNum.toInt() * 1000 / 100
        assertEquals(expectedMs.toLong(), it.delayMs)
      }
    }
  }

  @Test
  fun `test29 - zero numerator`() {
    val result = decode("test29.png")
    assertTrue(result.frames.size >= 2)
    assertTrue("Expected at least one zero-delay frame", result.frames.any { it.fcTL.delayNum.toInt() == 0 })
  }

  // -- num_plays --

  @Test
  fun `test30 - num_plays 0 means infinite`() {
    val result = decode("test30.png")
    assertEquals(Int.MAX_VALUE, result.metadata!!.numPlays)
  }

  @Test
  fun `test31 - num_plays 1`() {
    val result = decode("test31.png")
    assertEquals(1, result.metadata!!.numPlays)
  }

  @Test
  fun `test32 - num_plays 2`() {
    val result = decode("test32.png")
    assertEquals(2, result.metadata!!.numPlays)
  }

  // -- Other color depths and types --

  @Test
  fun `test33 - 16-bit colour`() {
    val result = decode("test33.png")
    assertTrue(result.frames.isNotEmpty())
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test34 - 8-bit greyscale`() {
    val result = decode("test34.png")
    assertTrue(result.frames.isNotEmpty())
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test35 - 8-bit greyscale and alpha with blending`() {
    val result = decode("test35.png")
    assertTrue(result.frames.isNotEmpty())
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test36 - 2-color palette`() {
    val result = decode("test36.png")
    assertTrue(result.frames.isNotEmpty())
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test37 - 2-bit palette and alpha`() {
    val result = decode("test37.png")
    assertTrue(result.frames.isNotEmpty())
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `test38 - 1-bit palette and alpha with blending`() {
    val result = decode("test38.png")
    assertTrue(result.frames.isNotEmpty())
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  // -- Real-world samples --

  @Test
  fun `ball - real world bouncing ball`() {
    val result = decode("ball.png")
    assertNotNull(result.metadata)
    assertTrue(result.frames.size > 1)
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `clock - real world clock with BLEND_OP_OVER`() {
    val result = decode("clock.png")
    assertNotNull(result.metadata)
    assertTrue(result.frames.size > 1)
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  @Test
  fun `elephant - real world elephant`() {
    val result = decode("elephant.png")
    assertNotNull(result.metadata)
    assertTrue(result.frames.size > 1)
    result.frames.forEach { assertNotNull(it.decodeBitmap()) }
  }

  // -- Helpers --

  private fun open(filename: String): InputStream {
    return javaClass.classLoader!!.getResourceAsStream("apng/$filename")
      ?: throw IllegalStateException("Test resource not found: apng/$filename")
  }

  private fun decode(filename: String): DecodeResult {
    val decoder = ApngDecoder(open(filename))
    val frames = decoder.debugGetAllFrames()
    return DecodeResult(decoder.metadata, frames)
  }

  private val ApngDecoder.Frame.delayMs: Long
    get() {
      val delayNumerator = fcTL.delayNum.toInt()
      val delayDenominator = fcTL.delayDen.toInt().takeIf { it > 0 } ?: 100
      return (delayNumerator * 1000L / delayDenominator)
    }

  private data class DecodeResult(
    val metadata: ApngDecoder.Metadata?,
    val frames: List<ApngDecoder.Frame>
  )
}
