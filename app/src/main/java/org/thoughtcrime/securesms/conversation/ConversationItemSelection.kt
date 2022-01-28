package org.thoughtcrime.securesms.conversation

import android.graphics.Bitmap
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

    val xTranslation = -conversationItem.x - conversationItem.bodyBubble.x
    val yTranslation = -conversationItem.y - conversationItem.bodyBubble.y

    val mp4Projection = conversationItem.getGiphyMp4PlayableProjection(list)
    var scaledVideoBitmap = videoBitmap
    if (videoBitmap != null) {
      scaledVideoBitmap = Bitmap.createScaledBitmap(
        videoBitmap,
        (videoBitmap.width / originalScale).toInt(),
        (videoBitmap.height / originalScale).toInt(),
        true
      )

      mp4Projection.translateX(xTranslation)
      mp4Projection.translateY(yTranslation)
      mp4Projection.applyToPath(path)
    }

    projections.use {
      it.forEach { p ->
        p.translateX(xTranslation)
        p.translateY(yTranslation)
        p.applyToPath(path)
      }
    }

    return createBitmap(conversationItem.bodyBubble.width, conversationItem.bodyBubble.height).applyCanvas {
      if (drawConversationItem) {
        conversationItem.bodyBubble.draw(this)
      }

      withClip(path) {
        withTranslation(x = xTranslation, y = yTranslation) {
          list.draw(this)

          if (scaledVideoBitmap != null) {
            drawBitmap(scaledVideoBitmap, mp4Projection.x - xTranslation, mp4Projection.y - yTranslation, null)
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
}
