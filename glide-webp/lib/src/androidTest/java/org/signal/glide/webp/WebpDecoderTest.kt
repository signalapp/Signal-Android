package org.signal.glide.webp

import android.os.Build
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.util.StreamUtil

@RunWith(AndroidJUnit4::class)
class WebpDecoderTest {
  private fun readAsset(name: String): ByteArray {
    val source = InstrumentationRegistry.getInstrumentation().context.assets.open(name)
    return StreamUtil.readFully(source)
  }

  @Test
  fun landscapeUnscaled() {
    val input = readAsset("landscape_550x368.webp")

    val outputUnconstrained = WebpDecoder().nativeDecodeBitmapScaled(input, 1000, 1000)!!
    assertEquals(550, outputUnconstrained.width)
    assertEquals(368, outputUnconstrained.height)

    val outputSquareConstraints = WebpDecoder().nativeDecodeBitmapScaled(input, 550, 550)!!
    assertEquals(550, outputSquareConstraints.width)
    assertEquals(368, outputSquareConstraints.height)

    val outputExactConstraints = WebpDecoder().nativeDecodeBitmapScaled(input, 550, 368)!!
    assertEquals(550, outputExactConstraints.width)
    assertEquals(368, outputExactConstraints.height)
  }

  @Test
  fun landscapeScaledBasedOnWidth() {
    val input = readAsset("landscape_550x368.webp")

    val outputUnconstrainedHeight = WebpDecoder().nativeDecodeBitmapScaled(input, 275, 1000)!!
    assertEquals(275, outputUnconstrainedHeight.width)
    assertEquals(184, outputUnconstrainedHeight.height)

    val outputSquareConstraints = WebpDecoder().nativeDecodeBitmapScaled(input, 275, 275)!!
    assertEquals(275, outputSquareConstraints.width)
    assertEquals(184, outputSquareConstraints.height)

    val outputSmallHeight = WebpDecoder().nativeDecodeBitmapScaled(input, 275, 200)!!
    assertEquals(275, outputSmallHeight.width)
    assertEquals(184, outputSmallHeight.height)
  }

  @Test
  fun landscapeScaledBasedOnHeight() {
    val input = readAsset("landscape_550x368.webp")

    val outputUnconstrainedWidth = WebpDecoder().nativeDecodeBitmapScaled(input, 1000, 184)!!
    assertEquals(275, outputUnconstrainedWidth.width)
    assertEquals(184, outputUnconstrainedWidth.height)

    val outputSmallWidth = WebpDecoder().nativeDecodeBitmapScaled(input, 300, 184)!!
    assertEquals(275, outputSmallWidth.width)
    assertEquals(184, outputSmallWidth.height)
  }

  private fun getNativeHeapSize(): Long {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Debug.getNativeHeapSize()
    } else {
      0
    }
  }

  @Test
  fun canScaleMassiveImagesWithoutAllocatingLargeBuffers() {
    val nativeHeapSizeAtStart = getNativeHeapSize()

    val input = readAsset("solid_white_10Kx10K.webp")

    val output = WebpDecoder().nativeDecodeBitmapScaled(input, 1000, 1000)!!
    assertEquals(1000, output.width)
    assertEquals(1000, output.height)

    var nativeHeapSizeAtEnd = getNativeHeapSize()
    assertTrue("heap size: " + nativeHeapSizeAtStart + " -> " + nativeHeapSizeAtEnd, nativeHeapSizeAtStart + 10_000 * 10_000 > nativeHeapSizeAtEnd)
  }

  @Test
  fun canHandleMassiveBrokenImagesWithoutAllocatingLargeBuffers() {
    val nativeHeapSizeAtStart = getNativeHeapSize()

    val input = readAsset("broken_10Kx10K.webp")

    val output = WebpDecoder().nativeDecodeBitmapScaled(input, 1000, 1000)
    assertNull(output)

    var nativeHeapSizeAtEnd = getNativeHeapSize()
    assertTrue("heap size: " + nativeHeapSizeAtStart + " -> " + nativeHeapSizeAtEnd, nativeHeapSizeAtStart + 10_000 * 10_000 > nativeHeapSizeAtEnd)
  }
}
