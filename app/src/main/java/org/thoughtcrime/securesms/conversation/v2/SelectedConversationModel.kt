package org.thoughtcrime.securesms.conversation.v2

import android.graphics.Bitmap
import android.view.View

/**
 * Contains information on a single selected conversation item. This is used when transitioning
 * between selected and unselected states.
 */
data class SelectedConversationModel(
  val bitmap: Bitmap,
  val bubbleX: Float,
  val bubbleY: Float,
  val bubbleWidth: Int,
  val isOutgoing: Boolean,
  val focusedView: View?,
)
