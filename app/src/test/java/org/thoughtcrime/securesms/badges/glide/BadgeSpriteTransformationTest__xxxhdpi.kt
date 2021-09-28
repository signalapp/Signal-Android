package org.thoughtcrime.securesms.badges.glide

import android.app.Application
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class BadgeSpriteTransformationTest__xxxhdpi {

  @Test
  fun `Given request for large xxxhdpi in light theme, when I getInBounds, then I expect 72x72@1,1`() {
    // GIVEN
    val density = "xxxhdpi"
    val size = BadgeSpriteTransformation.Size.LARGE
    val isDarkTheme = false

    // WHEN
    val inBounds = BadgeSpriteTransformation.getInBounds(density, size, isDarkTheme)

    // THEN
    assertRectMatches(inBounds, 1, 1, 72, 72)
  }

  @Test
  fun `Given request for large xxxhdpi in dark theme, when I getInBounds, then I expect 72x72@21,1`() {
    // GIVEN
    val density = "xxxhdpi"
    val size = BadgeSpriteTransformation.Size.LARGE
    val isDarkTheme = true

    // WHEN
    val inBounds = BadgeSpriteTransformation.getInBounds(density, size, isDarkTheme)

    // THEN
    assertRectMatches(inBounds, 75, 1, 72, 72)
  }

  @Test
  fun `Given request for medium xxxhdpi in light theme, when I getInBounds, then I expect 48x48@149,1`() {
    // GIVEN
    val density = "xxxhdpi"
    val size = BadgeSpriteTransformation.Size.MEDIUM
    val isDarkTheme = false

    // WHEN
    val inBounds = BadgeSpriteTransformation.getInBounds(density, size, isDarkTheme)

    // THEN
    assertRectMatches(inBounds, 149, 1, 48, 48)
  }

  @Test
  fun `Given request for medium xxxhdpi in dark theme, when I getInBounds, then I expect 48x48@199,1`() {
    // GIVEN
    val density = "xxxhdpi"
    val size = BadgeSpriteTransformation.Size.MEDIUM
    val isDarkTheme = true

    // WHEN
    val inBounds = BadgeSpriteTransformation.getInBounds(density, size, isDarkTheme)

    // THEN
    assertRectMatches(inBounds, 199, 1, 48, 48)
  }

  @Test
  fun `Given request for small xxxhdpi in light theme, when I getInBounds, then I expect 32x32@249,1`() {
    // GIVEN
    val density = "xxxhdpi"
    val size = BadgeSpriteTransformation.Size.SMALL
    val isDarkTheme = false

    // WHEN
    val inBounds = BadgeSpriteTransformation.getInBounds(density, size, isDarkTheme)

    // THEN
    assertRectMatches(inBounds, 249, 1, 32, 32)
  }

  @Test
  fun `Given request for small xxxhdpi in dark theme, when I getInBounds, then I expect 32x32@283,1`() {
    // GIVEN
    val density = "xxxhdpi"
    val size = BadgeSpriteTransformation.Size.SMALL
    val isDarkTheme = true

    // WHEN
    val inBounds = BadgeSpriteTransformation.getInBounds(density, size, isDarkTheme)

    // THEN
    assertRectMatches(inBounds, 283, 1, 32, 32)
  }

  @Test
  fun `Given request for xlarge xxxhdpi in light theme, when I getInBounds, then I expect 320x320@317,1`() {
    // GIVEN
    val density = "xxxhdpi"
    val size = BadgeSpriteTransformation.Size.XLARGE
    val isDarkTheme = false

    // WHEN
    val inBounds = BadgeSpriteTransformation.getInBounds(density, size, isDarkTheme)

    // THEN
    assertRectMatches(inBounds, 317, 1, 320, 320)
  }

  @Test
  fun `Given request for xlarge xxxhdpi in dark theme, when I getInBounds, then I expect 320x320@317,1`() {
    // GIVEN
    val density = "xxxhdpi"
    val size = BadgeSpriteTransformation.Size.XLARGE
    val isDarkTheme = true

    // WHEN
    val inBounds = BadgeSpriteTransformation.getInBounds(density, size, isDarkTheme)

    // THEN
    assertRectMatches(inBounds, 317, 1, 320, 320)
  }

  private fun assertRectMatches(rect: Rect, x: Int, y: Int, width: Int, height: Int) {
    assertEquals("Rect has wrong x value", x, rect.left)
    assertEquals("Rect has wrong y value", rect.top, y)
    assertEquals("Rect has wrong width", width, rect.width())
    assertEquals("Rect has wrong height", height, rect.height())
  }
}
