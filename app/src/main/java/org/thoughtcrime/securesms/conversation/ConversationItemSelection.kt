package org.thoughtcrime.securesms.conversation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.view.View
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.util.hasNoBubble

object ConversationItemSelection {

  @JvmStatic
  fun snapshotView(
    conversationItem: ConversationItem,
    list: RecyclerView,
    messageRecord: MessageRecord,
    videoBitmap: Bitmap?,
  ): Bitmap {
    val isOutgoing = messageRecord.isOutgoing
    val hasNoBubble = messageRecord.hasNoBubble(conversationItem.context)

    return snapshotMessage(
      conversationItem = conversationItem,
      list = list,
      videoBitmap = videoBitmap,
      drawConversationItem = !isOutgoing || hasNoBubble,
    )
  }

  private fun snapshotMessage(
    conversationItem: ConversationItem,
    list: RecyclerView,
    videoBitmap: Bitmap?,
    drawConversationItem: Boolean,
  ): Bitmap {
    val initialReactionVisibility = conversationItem.reactionsView.visibility
    if (initialReactionVisibility == View.VISIBLE) {
      conversationItem.reactionsView.visibility = View.INVISIBLE
    }

    val originalScale = conversationItem.bodyBubble.scaleX
    conversationItem.bodyBubble.scaleX = 1.0f
    conversationItem.bodyBubble.scaleY = 1.0f

    val projections = conversationItem.getColorizerProjections(list)

    val path = Path()

    val yTranslation = -conversationItem.y

    val mp4Projection = conversationItem.getGiphyMp4PlayableProjection(list)
    var scaledVideoBitmap = videoBitmap
    if (videoBitmap != null) {
      scaledVideoBitmap = Bitmap.createScaledBitmap(
        videoBitmap,
        (videoBitmap.width / originalScale).toInt(),
        (videoBitmap.height / originalScale).toInt(),
        true
      )

      mp4Projection.translateY(yTranslation)
      mp4Projection.applyToPath(path)
    }

    projections.use {
      it.forEach { p ->
        p.translateY(yTranslation)
        p.applyToPath(path)
      }
    }

    val distanceToBubbleBottom = conversationItem.bodyBubble.height + conversationItem.bodyBubble.y.toInt()
    return createBitmap(conversationItem.width, distanceToBubbleBottom).applyCanvas {
      if (drawConversationItem) {
        draw(conversationItem)
      }

      withClip(path) {
        withTranslation(y = yTranslation) {
          list.draw(this)

          if (scaledVideoBitmap != null) {
            drawBitmap(scaledVideoBitmap, mp4Projection.x, mp4Projection.y - yTranslation, null)
          }
        }
      }
    }.also {
      mp4Projection.release()
      conversationItem.reactionsView.visibility = initialReactionVisibility
      conversationItem.bodyBubble.scaleX = originalScale
      conversationItem.bodyBubble.scaleY = originalScale
    }
  }

  private fun Canvas.draw(conversationItem: ConversationItem) {
    val bodyBubble = conversationItem.bodyBubble
    withTranslation(bodyBubble.x, bodyBubble.y) {
      bodyBubble.draw(this@draw)
    }
  }
}
