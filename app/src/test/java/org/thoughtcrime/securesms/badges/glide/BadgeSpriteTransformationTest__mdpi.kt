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
class BadgeSpriteTransformationTest__mdpi {

  @Test
  fun `Given request for large mdpi in light theme, when I getInBounds, then I expect 18x18@1,1`() {
    // GIVEN
    val density = "mdpi"
    val size = BadgeSpriteTransformation.Size.LARGE
    val isDarkTheme = false

    // WHEN
    val inBounds = BadgeSpriteTransformation.getInBounds(density, size, isDarkTheme)

    // THEN
    assertRectMatches(inBounds, 1, 1, 18, 18)
  }

  @Test
  fun `Given request for large mdpi in dark theme, when I getInBounds, then I expect 18x18@21,1`() {
    // GIVEN
    val density = "mdpi"
    val size = BadgeSpriteTransformation.Size.LARGE
    val isDarkTheme = true

    // WHEN
    val inBounds = BadgeSpriteTransformation.getInBounds(density, size, isDarkTheme)

    // THEN
    assertRectMatches(inBounds, 21, 1, 18, 18)
  }

  @Test
  fun `Given request for medium mdpi in light theme, when I getInBounds, then I expect 12x12@41,1`() {
    // GIVEN
    val density = "mdpi"
    val size = BadgeSpriteTransformation.Size.MEDIUM
    val isDarkTheme = false

    // WHEN
    val inBounds = BadgeSpriteTransformation.getInBounds(density, size, isDarkTheme)

    // THEN
    assertRectMatches(inBounds, 41, 1, 12, 12)
  }

  @Test
  fun `Given request for medium mdpi in dark theme, when I getInBounds, then I expect 12x12@55,1`() {
    // GIVEN
    val density = "mdpi"
    val size = BadgeSpriteTransformation.Size.MEDIUM
    val isDarkTheme = true

    // WHEN
    val inBounds = BadgeSpriteTransformation.getInBounds(density, size, isDarkTheme)

    // THEN
    assertRectMatches(inBounds, 55, 1, 12, 12)
  }

  @Test
  fun `Given request for small mdpi in light theme, when I getInBounds, then I expect 8x8@69,1`() {
    // GIVEN
    val density = "mdpi"
    val size = BadgeSpriteTransformation.Size.SMALL
    val isDarkTheme = false

    // WHEN
    val inBounds = BadgeSpriteTransformation.getInBounds(density, size, isDarkTheme)

    // THEN
    assertRectMatches(inBounds, 69, 1, 8, 8)
  }

  @Test
  fun `Given request for small mdpi in dark theme, when I getInBounds, then I expect 8x8@79,1`() {
    // GIVEN
    val density = "mdpi"
    val size = BadgeSpriteTransformation.Size.SMALL
    val isDarkTheme = true

    // WHEN
    val inBounds = BadgeSpriteTransformation.getInBounds(density, size, isDarkTheme)

    // THEN
    assertRectMatches(inBounds, 79, 1, 8, 8)
  }

  @Test
  fun `Given request for xlarge mdpi in light theme, when I getInBounds, then I expect 80x80@89,1`() {
    // GIVEN
    val density = "mdpi"
    val size = BadgeSpriteTransformation.Size.XLARGE
    val isDarkTheme = false

    // WHEN
    val inBounds = BadgeSpriteTransformation.getInBounds(density, size, isDarkTheme)

    // THEN
    assertRectMatches(inBounds, 89, 1, 80, 80)
  }

  @Test
  fun `Given request for xlarge mdpi in dark theme, when I getInBounds, then I expect 80x80@89,1`() {
    // GIVEN
    val density = "mdpi"
    val size = BadgeSpriteTransformation.Size.XLARGE
    val isDarkTheme = true

    // WHEN
    val inBounds = BadgeSpriteTransformation.getInBounds(density, size, isDarkTheme)

    // THEN
    assertRectMatches(inBounds, 89, 1, 80, 80)
  }

  private fun assertRectMatches(rect: Rect, x: Int, y: Int, width: Int, height: Int) {
    assertEquals("Rect has wrong x value", x, rect.left)
    assertEquals("Rect has wrong y value", rect.top, y)
    assertEquals("Rect has wrong width", width, rect.width())
    assertEquals("Rect has wrong height", height, rect.height())
  }
}
