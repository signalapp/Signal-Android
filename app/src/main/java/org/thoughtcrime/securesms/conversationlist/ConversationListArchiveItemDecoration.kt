package org.thoughtcrime.securesms.conversationlist

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.time.Duration.Companion.seconds

/**
 * When an item is removed, the gap is held by the items below the space being translated down, OR the items above the space being translated up, OR both.
 * Then the items are animated to close the gap.
 *
 * So what we want to do is find that gap and fill it with color to give the illusion of the archived row being covered up.
 *
 * We want to be careful to only draw this for removals due to archiving, and we also don't want to screw up interactions with the pinned chat headers.
 */
class ConversationListArchiveItemDecoration(val background: Drawable) : RecyclerView.ItemDecoration() {

  companion object {
    /** Upper bound on how long after a swipe we're willing to fill a gap, so a missed animation can't leave us armed indefinitely. */
    private val ARCHIVE_WINDOW = 1.seconds.inWholeMilliseconds
  }

  private var archiveTriggeredAt: Long = 0
  private var archiveAnimationStarted: Boolean = false

  override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    if (archiveTriggeredAt == 0L) {
      return
    }

    if (SystemClock.elapsedRealtime() - archiveTriggeredAt > ARCHIVE_WINDOW) {
      clearArchiveTrigger()
      return
    }

    if (parent.isAnimating) {
      archiveAnimationStarted = true
    } else if (archiveAnimationStarted) {
      clearArchiveTrigger()
      return
    }

    val childCount = parent.layoutManager?.childCount ?: 0

    var lastViewComingDown: View? = null
    var firstViewComingUp: View? = null

    for (i in 0 until childCount) {
      val child: View? = parent.layoutManager?.getChildAt(i)
      val childHolder: RecyclerView.ViewHolder? = if (child != null) parent.getChildViewHolder(child) else null

      if (child != null && childHolder != null) {
        if (child.translationY < 0) {
          lastViewComingDown = child
        } else if (child.translationY > 0 && firstViewComingUp == null) {
          firstViewComingUp = child
        }
      }
    }

    var top = 0
    var bottom = 0

    if (lastViewComingDown != null && firstViewComingUp != null) {
      top = lastViewComingDown.bottom + lastViewComingDown.translationY.toInt()
      bottom = firstViewComingUp.top + firstViewComingUp.translationY.toInt()
    } else if (lastViewComingDown != null) {
      top = lastViewComingDown.bottom + lastViewComingDown.translationY.toInt()
      bottom = lastViewComingDown.bottom
    } else if (firstViewComingUp != null) {
      top = firstViewComingUp.top
      bottom = firstViewComingUp.top + firstViewComingUp.translationY.toInt()
    }

    val gapHeight = bottom - top
    val singleItemHeight = when {
      firstViewComingUp != null -> firstViewComingUp.height
      lastViewComingDown != null -> lastViewComingDown.height
      else -> 0
    }

    // A bit unscientific, but this gives us the behavior we want around archiving things in the pinned chat section
    if (gapHeight > singleItemHeight * 2) {
      clearArchiveTrigger()
      return
    }

    background.setBounds(0, top, parent.width, bottom)
    background.draw(canvas)
  }

  fun onArchiveStarted() {
    archiveTriggeredAt = SystemClock.elapsedRealtime()
    archiveAnimationStarted = false
  }

  private fun clearArchiveTrigger() {
    archiveTriggeredAt = 0
    archiveAnimationStarted = false
  }
}
