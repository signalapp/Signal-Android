package org.thoughtcrime.securesms.badges.gifts

import org.thoughtcrime.securesms.util.Projection

/**
 * Notes that a given item can have a gift box drawn over it.
 */
interface OpenableGift {
  /**
   * Returns a projection to draw a top, or null to not do so.
   */
  fun getOpenableGiftProjection(isAnimating: Boolean): Projection?

  /**
   * Returns a unique id assosicated with this gift.
   */
  fun getGiftId(): Long

  /**
   * Registers a callback to start the open animation
   */
  fun setOpenGiftCallback(openGift: (OpenableGift) -> Unit)

  /**
   * Clears any callback created to start the open animation
   */
  fun clearOpenGiftCallback()

  /**
   * Gets the appropriate sign for the animation evaluators:
   *
   * - Incoming and LTR -> Positive
   * - Incoming and RTL -> Negative
   * - Outgoing and LTR -> Negative
   * - Outgoing and RTL -> Positive
   */
  fun getAnimationSign(): AnimationSign

  enum class AnimationSign(val sign: Float) {
    POSITIVE(1f),
    NEGATIVE(-1f);

    companion object {
      @JvmStatic
      fun get(isLtr: Boolean, isOutgoing: Boolean): AnimationSign {
        return when {
          isLtr && isOutgoing -> NEGATIVE
          isLtr -> POSITIVE
          isOutgoing -> POSITIVE
          else -> NEGATIVE
        }
      }
    }
  }
}
