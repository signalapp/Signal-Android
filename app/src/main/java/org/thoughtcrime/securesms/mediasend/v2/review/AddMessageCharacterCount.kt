package org.thoughtcrime.securesms.mediasend.v2.review

import org.thoughtcrime.securesms.stories.Stories
import kotlin.math.max

/**
 * Manages the current character count for the add-message input.
 *
 * We only want to display the count if DISPLAY_COUNT or less characters remain.
 * The actual count is calculated in the background by the ViewModel.
 */
@JvmInline
value class AddMessageCharacterCount(private val count: Int) {
  fun getRemaining(): Int = max(Stories.MAX_CAPTION_SIZE - count, 0)

  fun shouldDisplayCount(): Boolean = getRemaining() <= DISPLAY_COUNT_THRESHOLD

  companion object {
    private const val DISPLAY_COUNT_THRESHOLD = 50
  }
}
