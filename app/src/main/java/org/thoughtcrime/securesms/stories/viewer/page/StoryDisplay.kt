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

    fun getStoryDisplay(resources: Resources): StoryDisplay {
      val aspectRatio = resources.displayMetrics.widthPixels.toFloat() / resources.displayMetrics.heightPixels

      return when {
        aspectRatio >= LANDSCAPE -> MEDIUM
        aspectRatio >= LARGE_AR -> LARGE
        aspectRatio <= SMALL_AR -> SMALL
        else -> MEDIUM
      }
    }
  }
}
