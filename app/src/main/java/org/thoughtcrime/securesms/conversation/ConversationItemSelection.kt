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
import org.thoughtcrime.securesms.conversation.v2.items.InteractiveConversationElement
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4Playable
import org.thoughtcrime.securesms.util.hasNoBubble

object ConversationItemSelection {

  @JvmStatic
  fun snapshotView(
    target: InteractiveConversationElement,
    list: RecyclerView,
    messageRecord: MessageRecord,
    videoBitmap: Bitmap?
  ): Bitmap {
    val isOutgoing = messageRecord.isOutgoing
    val hasNoBubble = messageRecord.hasNoBubble(list.context)

    return snapshotMessage(
      target = target,
      list = list,
      videoBitmap = videoBitmap,
      drawConversationItem = (!isOutgoing || hasNoBubble),
      hasReaction = messageRecord.reactions.isNotEmpty()
    )
  }

  private fun snapshotMessage(
    target: InteractiveConversationElement,
    list: RecyclerView,
    videoBitmap: Bitmap?,
    drawConversationItem: Boolean,
    hasReaction: Boolean
  ): Bitmap {
    val snapshotStrategy = target.getSnapshotStrategy()
    if (snapshotStrategy != null) {
      return createBitmap(target.root.width, target.root.height).applyCanvas {
        snapshotStrategy.snapshot(this)
      }
    }

    val bodyBubble = target.bubbleView
    val reactionsView = target.reactionsView

    val originalScale = bodyBubble.scaleX
    bodyBubble.scaleX = 1.0f
    bodyBubble.scaleY = 1.0f

    val projections = target.getSnapshotProjections(list, false)

    val path = Path()

    val xTranslation = -target.root.x - bodyBubble.x
    val yTranslation = -target.root.y - bodyBubble.y

    val mp4Projection = (target as? GiphyMp4Playable)?.getGiphyMp4PlayableProjection(list)

    var scaledVideoBitmap: Bitmap? = null
    if (videoBitmap != null && mp4Projection != null) {
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

    target.root.destroyAllDrawingCaches()

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

          if (scaledVideoBitmap != null && mp4Projection != null) {
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
      mp4Projection?.release()
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
