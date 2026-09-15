/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import org.signal.core.util.Util
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Where the long press overlay puts the message snapshot, the emoji strip and the action menu.
 *
 * Overlay pixels, y growing downwards, origin under the status bar. A strip pushed above the message
 * can legitimately land at a negative y, down to -[Metrics.statusBarHeight].
 */
object ReactionOverlayPlacement {

  /**
   * @param overlayHeight Status bar included; [navigationBarHeight] comes off before anything is placed.
   * @param snapshotHeight Height of the snapshot bitmap, which is also the snapshot view's height.
   * @param isMessageOnLeft Whether the snapshot hangs off the leading edge.
   * @param lastSeenDownY Where the long press landed, in window coordinates.
   */
  data class Metrics(
    val overlayWidth: Int,
    val overlayHeight: Int,
    val statusBarHeight: Int,
    val navigationBarHeight: Int,
    val bubbleX: Float,
    val bubbleY: Float,
    val bubbleWidth: Int,
    val contextMenuX: Float,
    val snapshotWidth: Int,
    val snapshotHeight: Int,
    val reactionBarHeight: Int,
    val scrubberForegroundHeight: Int,
    val scrubberWidth: Int,
    val scrubberHorizontalMargin: Int,
    val menuMaxWidth: Int,
    val menuMaxHeight: Int,
    val isMessageOnLeft: Boolean,
    val lastSeenDownY: Float
  )

  /**
   * @param snapshotScale Shrinks the snapshot when there is no room for it at full size.
   * @param isWideLayout True when the menu fits beside the strip rather than under the message.
   * @param menuHeight Height to hold the menu to, or null to leave it at its natural maximum.
   */
  data class Placement(
    val snapshotX: Float,
    val snapshotY: Float,
    val snapshotScale: Float,
    val reactionBarY: Float,
    val scrubberX: Float,
    val scrubberForegroundY: Float,
    val isWideLayout: Boolean,
    val menuHeight: Int?,
    val menuOffsetX: Float,
    val menuOffsetY: Float
  )

  private const val MENU_PADDING_DP = 12f
  private const val REACTION_BAR_TOP_PADDING_DP = 32f
  private const val REACTION_BAR_OFFSET_DP = 48f

  /** Below this, the strip pins to the message instead of the touch so it cannot crowd the menu. */
  private const val TIGHT_MENU_THRESHOLD_DP = 150f

  fun of(metrics: Metrics, dp: DpConverter): Placement {
    val menuPadding = dp.toPx(MENU_PADDING_DP)
    val reactionBarTopPadding = dp.toPx(REACTION_BAR_TOP_PADDING_DP)
    val reactionBarOffset = dp.toPx(REACTION_BAR_OFFSET_DP)
    val tightMenuThreshold = dp.toPx(TIGHT_MENU_THRESHOLD_DP)

    val isWideLayout = metrics.menuMaxWidth + metrics.scrubberWidth < metrics.overlayWidth
    val overlayHeight = metrics.overlayHeight - metrics.navigationBarHeight
    val reactionBarHeight = metrics.reactionBarHeight
    val snapshotHeight = metrics.snapshotHeight
    val horizontalScaleSign = if (metrics.isMessageOnLeft) -1 else 1

    var endX = metrics.bubbleX
    var endY = metrics.bubbleY
    var endApparentTop = endY
    var endScale = 1f
    var menuHeightOverride: Int? = null
    val reactionBarY: Float

    if (isWideLayout) {
      val everythingFitsVertically = reactionBarHeight + menuPadding + reactionBarTopPadding + snapshotHeight < overlayHeight

      if (everythingFitsVertically) {
        val reactionBarFitsAboveItem = metrics.bubbleY > reactionBarHeight + menuPadding + reactionBarTopPadding

        if (reactionBarFitsAboveItem) {
          reactionBarY = metrics.bubbleY - menuPadding - reactionBarHeight
        } else {
          endY = reactionBarHeight + menuPadding + reactionBarTopPadding
          reactionBarY = reactionBarTopPadding
        }
      } else {
        val spaceAvailableForItem = overlayHeight - reactionBarHeight - menuPadding - reactionBarTopPadding

        endScale = spaceAvailableForItem / snapshotHeight
        endX += Util.halfOffsetFromScale(metrics.snapshotWidth, endScale) * horizontalScaleSign
        endY = reactionBarHeight + menuPadding + reactionBarTopPadding - Util.halfOffsetFromScale(snapshotHeight, endScale)
        reactionBarY = reactionBarTopPadding
      }
    } else {
      val spaceForReactionBar = max(reactionBarHeight + reactionBarOffset - snapshotHeight, 0f)
      val everythingFitsVertically = metrics.menuMaxHeight + snapshotHeight + menuPadding + spaceForReactionBar < overlayHeight

      if (everythingFitsVertically) {
        val bubbleBottom = metrics.bubbleY + snapshotHeight
        val menuFitsBelowItem = bubbleBottom + menuPadding + metrics.menuMaxHeight <= overlayHeight

        if (menuFitsBelowItem) {
          if (metrics.bubbleY < 0) {
            endY = 0f
          }

          reactionBarY = barOffsetForTouch(
            metrics = metrics,
            contextMenuTop = endY + snapshotHeight,
            menuPadding = menuPadding,
            reactionBarOffset = reactionBarOffset,
            reactionBarTopPadding = reactionBarTopPadding,
            tightMenuThreshold = tightMenuThreshold,
            messageTop = endY
          )

          if (reactionBarY <= reactionBarTopPadding) {
            endY = reactionBarHeight + menuPadding + reactionBarTopPadding
          }
        } else {
          endY = overlayHeight - metrics.menuMaxHeight - menuPadding - snapshotHeight

          reactionBarY = barOffsetForTouch(
            metrics = metrics,
            contextMenuTop = endY + snapshotHeight,
            menuPadding = menuPadding,
            reactionBarOffset = reactionBarOffset,
            reactionBarTopPadding = reactionBarTopPadding,
            tightMenuThreshold = tightMenuThreshold,
            messageTop = endY
          )
        }

        endApparentTop = endY
      } else if (reactionBarOffset + reactionBarHeight + metrics.menuMaxHeight + menuPadding < overlayHeight) {
        val spaceAvailableForItem = overlayHeight - metrics.menuMaxHeight - menuPadding - spaceForReactionBar

        endScale = spaceAvailableForItem / snapshotHeight
        endX += Util.halfOffsetFromScale(metrics.snapshotWidth, endScale) * horizontalScaleSign
        endY = spaceForReactionBar - Util.halfOffsetFromScale(snapshotHeight, endScale)

        val halfOffset = Util.halfOffsetFromScale(snapshotHeight, endScale)

        reactionBarY = barOffsetForTouch(
          metrics = metrics,
          contextMenuTop = endY + (snapshotHeight * endScale) + halfOffset,
          menuPadding = menuPadding,
          reactionBarOffset = reactionBarOffset,
          reactionBarTopPadding = reactionBarTopPadding,
          tightMenuThreshold = tightMenuThreshold,
          messageTop = endY
        )

        endApparentTop = endY + halfOffset
      } else {
        val menuHeight = metrics.menuMaxHeight / 2
        menuHeightOverride = menuHeight

        val fitsVertically = menuHeight + snapshotHeight + menuPadding * 2 + reactionBarHeight + reactionBarTopPadding < overlayHeight

        if (fitsVertically) {
          val bubbleBottom = metrics.bubbleY + snapshotHeight
          val menuFitsBelowItem = bubbleBottom + menuPadding + menuHeight <= overlayHeight

          if (menuFitsBelowItem) {
            val aboveItem = metrics.bubbleY - menuPadding - reactionBarHeight

            if (aboveItem < reactionBarTopPadding) {
              endY = reactionBarTopPadding + reactionBarHeight + menuPadding
              reactionBarY = reactionBarTopPadding
            } else {
              reactionBarY = aboveItem
            }
          } else {
            endY = overlayHeight - menuHeight - menuPadding - snapshotHeight
            reactionBarY = endY - reactionBarHeight - menuPadding
          }

          endApparentTop = endY
        } else {
          val spaceAvailableForItem = overlayHeight - menuHeight - menuPadding * 2 - reactionBarHeight - reactionBarTopPadding

          endScale = spaceAvailableForItem / snapshotHeight
          endX += Util.halfOffsetFromScale(metrics.snapshotWidth, endScale) * horizontalScaleSign
          endY = reactionBarHeight - Util.halfOffsetFromScale(snapshotHeight, endScale) + menuPadding + reactionBarTopPadding
          reactionBarY = reactionBarTopPadding
          endApparentTop = reactionBarHeight + menuPadding + reactionBarTopPadding
        }
      }
    }

    val clampedReactionBarY = max(reactionBarY, -metrics.statusBarHeight.toFloat())

    val scrubberX = if (metrics.isMessageOnLeft) {
      metrics.scrubberHorizontalMargin.toFloat()
    } else {
      (metrics.overlayWidth - metrics.scrubberWidth - metrics.scrubberHorizontalMargin).toFloat()
    }

    val menuOffsetX: Float
    val menuOffsetY: Float

    if (isWideLayout) {
      val scrubberRight = scrubberX + metrics.scrubberWidth

      menuOffsetX = if (metrics.isMessageOnLeft) scrubberRight + menuPadding else scrubberX - metrics.menuMaxWidth - menuPadding
      menuOffsetY = min(clampedReactionBarY, (overlayHeight - metrics.menuMaxHeight).toFloat())
    } else {
      menuOffsetX = if (metrics.isMessageOnLeft) {
        metrics.contextMenuX
      } else {
        -metrics.menuMaxWidth + metrics.contextMenuX + metrics.bubbleWidth
      }
      menuOffsetY = endApparentTop + (snapshotHeight * endScale) + menuPadding
    }

    return Placement(
      snapshotX = endX,
      snapshotY = endY,
      snapshotScale = endScale,
      reactionBarY = clampedReactionBarY,
      scrubberX = scrubberX,
      scrubberForegroundY = clampedReactionBarY + reactionBarHeight / 2f - metrics.scrubberForegroundHeight / 2f,
      isWideLayout = isWideLayout,
      menuHeight = menuHeightOverride,
      menuOffsetX = menuOffsetX,
      menuOffsetY = menuOffsetY
    )
  }

  /** Puts the strip above whichever is higher, the long press or the menu, so it lands under the thumb. */
  private fun barOffsetForTouch(
    metrics: Metrics,
    contextMenuTop: Float,
    menuPadding: Float,
    reactionBarOffset: Float,
    reactionBarTopPadding: Float,
    tightMenuThreshold: Float,
    messageTop: Float
  ): Float {
    val adjustedTouchY = metrics.lastSeenDownY - metrics.statusBarHeight
    var reactionStartingPoint = min(adjustedTouchY, contextMenuTop)

    if (abs(messageTop - contextMenuTop) < tightMenuThreshold) {
      reactionStartingPoint = messageTop + (reactionBarOffset - menuPadding)
    }

    return max(reactionStartingPoint - reactionBarOffset - metrics.reactionBarHeight, reactionBarTopPadding)
  }

  /** Keeps the dp constants convertible without Resources, so this stays testable off device. */
  fun interface DpConverter {
    fun toPx(dp: Float): Float
  }
}
