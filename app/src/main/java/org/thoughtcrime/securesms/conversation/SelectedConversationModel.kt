package org.thoughtcrime.securesms.conversation

import android.graphics.Bitmap
import android.net.Uri
import android.view.View

/**
 * Contains information on a single selected conversation item. This is used when transitioning
 * between selected and unselected states.
 */
data class SelectedConversationModel(
  val bitmap: Bitmap,
  val itemX: Float,
  val itemY: Float,
  val bubbleX: Float,
  val bubbleY: Float,
  val bubbleWidth: Int,
  val audioUri: Uri? = null,
  val isOutgoing: Boolean,
  val focusedView: View?
)
