package org.thoughtcrime.securesms.stories.viewer.page

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class StoryDisplayTest(
  private val width: Float,
  private val height: Float,
  private val storyDisplay: StoryDisplay
) {

  @Test
  fun `Given an aspect ratio, when I getStoryDisplay, then I expect correct size`() {
    assertEquals(storyDisplay, StoryDisplay.getStoryDisplay(width, height))
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{index}: displaySize({0}, {1}) = {2}")
    fun data(): Iterable<Array<Any>> = arrayListOf(
      arrayOf(9f, 20.1f, StoryDisplay.LARGE),
      arrayOf(4f, 3f, StoryDisplay.MEDIUM),
      arrayOf(9, 18f, StoryDisplay.LARGE),
      arrayOf(9, 17f, StoryDisplay.MEDIUM),
      arrayOf(9, 16f, StoryDisplay.SMALL)
    )
  }
}
