package org.thoughtcrime.securesms.conversation

import android.graphics.Bitmap
import android.graphics.Path
import android.view.ViewGroup
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.util.hasNoBubble

object ConversationItemSelection {

  @JvmStatic
  fun snapshotView(
    conversationItem: ConversationItem,
    list: RecyclerView,
    messageRecord: MessageRecord,
    videoBitmap: Bitmap?
  ): Bitmap {
    val isOutgoing = messageRecord.isOutgoing
    val hasNoBubble = messageRecord.hasNoBubble(conversationItem.context)

    return snapshotMessage(
      conversationItem = conversationItem,
      list = list,
      videoBitmap = videoBitmap,
      drawConversationItem = !isOutgoing || hasNoBubble,
      hasReaction = messageRecord.reactions.isNotEmpty()
    )
  }

  private fun snapshotMessage(
    conversationItem: ConversationItem,
    list: RecyclerView,
    videoBitmap: Bitmap?,
    drawConversationItem: Boolean,
    hasReaction: Boolean
  ): Bitmap {
    val bodyBubble = conversationItem.bodyBubble
    val reactionsView = conversationItem.reactionsView

    val originalScale = bodyBubble.scaleX
    bodyBubble.scaleX = 1.0f
    bodyBubble.scaleY = 1.0f

    val projections = conversationItem.getSnapshotProjections(list, false)

    val path = Path()

    val xTranslation = -conversationItem.x - bodyBubble.x
    val yTranslation = -conversationItem.y - bodyBubble.y

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

    conversationItem.destroyAllDrawingCaches()

    var bitmapHeight = bodyBubble.height
    if (hasReaction) {
      bitmapHeight += (reactionsView.height - DimensionUnit.DP.toPixels(4f)).toInt()
    }
    return createBitmap(bodyBubble.width, bitmapHeight).applyCanvas {
      if (drawConversationItem) {
        bodyBubble.draw(this)
      }

      withClip(path) {
        withTranslation(x = xTranslation, y = yTranslation) {
          list.draw(this)

          if (scaledVideoBitmap != null) {
            drawBitmap(scaledVideoBitmap, mp4Projection.x - xTranslation, mp4Projection.y - yTranslation, null)
          }
        }
      }

      withTranslation(
        x = reactionsView.x - bodyBubble.x,
        y = reactionsView.y - bodyBubble.y
      ) {
        reactionsView.draw(this)
      }
    }.also {
      mp4Projection.release()
      bodyBubble.scaleX = originalScale
      bodyBubble.scaleY = originalScale
    }
  }
}

private fun ViewGroup.destroyAllDrawingCaches() {
  children.forEach {
    it.destroyDrawingCache()

    if (it is ViewGroup) {
      it.destroyAllDrawingCaches()
    }
  }
}
