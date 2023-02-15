package org.thoughtcrime.securesms.stories.viewer.page

import android.content.res.Resources

/**
 * Given the size of our display, we render the story overlay / crop in one of 3 ways.
 */
enum class StoryDisplay {
  /**
   * View/Reply is underneath story content, corners are rounded, content is not cropped
   */
  LARGE,

  /**
   * View/Reply overlays story content, corners are rounded, content is not cropped
   */
  MEDIUM,

  /**
   * View/Reply is overlays story content, corners are not rounded, content is cropped
   */
  SMALL;

  companion object {
    private const val LANDSCAPE = 1f
    private const val LARGE_AR = 9 / 18f
    private const val SMALL_AR = 9 / 16f

    fun getStoryDisplay(screenWidth: Float, screenHeight: Float): StoryDisplay {
      val aspectRatio = screenWidth / screenHeight

      return when {
        aspectRatio >= LANDSCAPE -> MEDIUM
        aspectRatio <= LARGE_AR -> LARGE
        aspectRatio >= SMALL_AR -> SMALL
        else -> MEDIUM
      }
    }

    fun getStorySize(resources: Resources): Size {
      val width = resources.displayMetrics.widthPixels.toFloat()
      val height = resources.displayMetrics.heightPixels.toFloat()
      val storyDisplay = getStoryDisplay(width, height)

      val (imageWidth, imageHeight) = when (storyDisplay) {
        LARGE -> width to width * 16 / 9
        MEDIUM -> width to width * 16 / 9
        SMALL -> width to height
      }

      return Size(imageWidth.toInt(), imageHeight.toInt())
    }
  }

  /**
   * Android Size() is limited to API 21+
   */
  data class Size(val width: Int, val height: Int)
}
