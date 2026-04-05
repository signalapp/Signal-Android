package org.thoughtcrime.securesms.util

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenDensityTest {

  @Test
  fun `get returns ldpi for DENSITY_LOW`() {
    val result = ScreenDensity.get(contextWithDensity(DisplayMetrics.DENSITY_LOW))
    assertEquals("ldpi", result.bucket)
    assertTrue(result.isKnownDensity)
  }

  @Test
  fun `get returns mdpi for DENSITY_MEDIUM`() {
    val result = ScreenDensity.get(contextWithDensity(DisplayMetrics.DENSITY_MEDIUM))
    assertEquals("mdpi", result.bucket)
  }

  @Test
  fun `get returns hdpi for DENSITY_HIGH`() {
    val result = ScreenDensity.get(contextWithDensity(DisplayMetrics.DENSITY_HIGH))
    assertEquals("hdpi", result.bucket)
  }

  @Test
  fun `get returns xhdpi for DENSITY_XHIGH`() {
    val result = ScreenDensity.get(contextWithDensity(DisplayMetrics.DENSITY_XHIGH))
    assertEquals("xhdpi", result.bucket)
  }

  @Test
  fun `get returns xxhdpi for DENSITY_XXHIGH`() {
    val result = ScreenDensity.get(contextWithDensity(DisplayMetrics.DENSITY_XXHIGH))
    assertEquals("xxhdpi", result.bucket)
  }

  @Test
  fun `get returns xxxhdpi for DENSITY_XXXHIGH`() {
    val result = ScreenDensity.get(contextWithDensity(DisplayMetrics.DENSITY_XXXHIGH))
    assertEquals("xxxhdpi", result.bucket)
  }

  @Test
  fun `get returns hdpi for density between mdpi and hdpi`() {
    val result = ScreenDensity.get(contextWithDensity(200))
    assertEquals("mdpi", result.bucket)
  }

  @Test
  fun `get returns xhdpi for density between hdpi and xhdpi`() {
    val result = ScreenDensity.get(contextWithDensity(280))
    assertEquals("hdpi", result.bucket)
  }

  @Test
  fun `get returns xxxhdpi for density above DENSITY_XXXHIGH`() {
    val result = ScreenDensity.get(contextWithDensity(800))
    assertEquals("xxxhdpi", result.bucket)
    assertTrue(result.isKnownDensity)
  }

  @Test
  fun `get returns unknown for density below DENSITY_LOW`() {
    val result = ScreenDensity.get(contextWithDensity(50))
    assertFalse(result.isKnownDensity)
  }

  @Test
  fun `toString contains bucket and density`() {
    val result = ScreenDensity.get(contextWithDensity(DisplayMetrics.DENSITY_XHIGH))
    assertEquals("xhdpi (320)", result.toString())
  }

  @Test
  fun `xhdpiRelativeDensityScaleFactor returns correct values`() {
    assertEquals(0.25f, ScreenDensity.xhdpiRelativeDensityScaleFactor("ldpi"))
    assertEquals(0.5f, ScreenDensity.xhdpiRelativeDensityScaleFactor("mdpi"))
    assertEquals(0.75f, ScreenDensity.xhdpiRelativeDensityScaleFactor("hdpi"))
    assertEquals(1f, ScreenDensity.xhdpiRelativeDensityScaleFactor("xhdpi"))
  }

  private fun contextWithDensity(densityDpi: Int): Context {
    val displayMetrics = DisplayMetrics().apply { this.densityDpi = densityDpi }
    val resources = mockk<Resources> {
      every { this@mockk.displayMetrics } returns displayMetrics
    }
    return mockk {
      every { this@mockk.resources } returns resources
    }
  }
}
