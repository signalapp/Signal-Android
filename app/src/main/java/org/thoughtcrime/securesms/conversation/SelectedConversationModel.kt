package org.thoughtcrime.securesms.conversation

import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import android.view.View

/**
 * Contains information on a single selected conversation item. This is used when transitioning
 * between selected and unselected states.
 *
 * Coordinates are in the reaction overlay's space, not the list's.
 *
 * @param bubbleX      Left edge of the captured snapshot.
 * @param bubbleY      Top edge of the captured bubble.
 * @param contextMenuX Left edge the context menu lines up with.
 */
data class SelectedConversationModel(
  val bitmap: Bitmap,
  val bubbleX: Float,
  val bubbleY: Float,
  val bubbleWidth: Int,
  val contextMenuX: Float,
  val audioUri: Uri? = null,
  val isOutgoing: Boolean,
  val focusedView: View?,
  val returnPosition: ReturnPosition
) {

  /** Where the snapshot animates back to, read on dismiss so a list that moved is followed. */
  fun interface ReturnPosition {
    /** @return The row's current position, or null if it is gone. */
    fun get(): PointF?
  }
}
