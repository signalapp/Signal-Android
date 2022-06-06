package org.thoughtcrime.securesms.conversation.colors.ui

import android.content.Context
import android.content.res.TypedArray
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.DeliveryStatusView
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.colors.Colorizer
import org.thoughtcrime.securesms.conversation.colors.ColorizerView
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.Projection
import org.thoughtcrime.securesms.util.ThemeUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper
import java.util.Locale

private val TAG = Log.tag(ChatColorPreviewView::class.java)

class ChatColorPreviewView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

  private val wallpaper: ImageView
  private val wallpaperDim: View
  private val colorizerView: ColorizerView

  private val recv1: Bubble
  private val sent1: Bubble
  private val recv2: Bubble
  private val sent2: Bubble

  private val bubbleCount: Int
  private val colorizer: Colorizer

  private var chatColors: ChatColors? = null

  init {
    inflate(context, R.layout.chat_colors_preview_view, this)

    var typedArray: TypedArray? = null
    try {
      typedArray = context.obtainStyledAttributes(attrs, R.styleable.ChatColorPreviewView, 0, 0)

      bubbleCount = typedArray.getInteger(R.styleable.ChatColorPreviewView_ccpv_chat_bubble_count, 2)

      assert(bubbleCount == 2 || bubbleCount == 4) {
        Log.e(TAG, "Bubble count must be 2 or 4")
      }

      recv1 = Bubble(
        findViewById(R.id.bubble_1),
        findViewById(R.id.bubble_1_text),
        findViewById(R.id.bubble_1_time),
        null
      )

      sent1 = Bubble(
        findViewById(R.id.bubble_2),
        findViewById(R.id.bubble_2_text),
        findViewById(R.id.bubble_2_time),
        findViewById(R.id.bubble_2_delivery)
      )

      recv2 = Bubble(
        findViewById(R.id.bubble_3),
        findViewById(R.id.bubble_3_text),
        findViewById(R.id.bubble_3_time),
        null
      )

      sent2 = Bubble(
        findViewById(R.id.bubble_4),
        findViewById(R.id.bubble_4_text),
        findViewById(R.id.bubble_4_time),
        findViewById(R.id.bubble_4_delivery)
      )

      val now: String = DateUtils.getExtendedRelativeTimeSpanString(context, Locale.getDefault(), System.currentTimeMillis())
      listOf(sent1, sent2, recv1, recv2).forEach {
        it.time.text = now
        it.delivery?.setRead()
      }

      if (bubbleCount == 2) {
        recv2.bubble.visibility = View.GONE
        sent2.bubble.visibility = View.GONE
      }

      wallpaper = findViewById(R.id.wallpaper)
      wallpaperDim = findViewById(R.id.wallpaper_dim)
      colorizerView = findViewById(R.id.colorizer)
      colorizer = Colorizer()
    } finally {
      typedArray?.recycle()
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()

    redrawChatColors()
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)

    redrawChatColors()
  }

  private fun redrawChatColors() {
    if (chatColors != null) {
      setChatColors(requireNotNull(chatColors))
    }
  }

  fun setWallpaper(chatWallpaper: ChatWallpaper?) {
    if (chatWallpaper != null) {
      chatWallpaper.loadInto(wallpaper)

      if (ThemeUtil.isDarkTheme(context)) {
        wallpaperDim.alpha = chatWallpaper.dimLevelForDarkTheme
      } else {
        wallpaperDim.alpha = 0f
      }
    } else {
      wallpaper.background = null
      wallpaperDim.alpha = 0f
    }

    val backgroundColor = if (chatWallpaper != null) {
      R.color.conversation_item_recv_bubble_color_wallpaper
    } else {
      R.color.signal_background_secondary
    }

    listOf(recv1, recv2).forEach {
      it.bubble.background.colorFilter = PorterDuffColorFilter(
        ContextCompat.getColor(context, backgroundColor),
        PorterDuff.Mode.SRC_IN
      )
    }
  }

  fun setChatColors(chatColors: ChatColors) {
    this.chatColors = chatColors

    val sentBubbles = listOf(sent1, sent2)

    sentBubbles.forEach {
      it.bubble.background.colorFilter = chatColors.chatBubbleColorFilter
    }

    val mask: Drawable = chatColors.chatBubbleMask
    val bubbles = if (bubbleCount == 4) {
      listOf(sent1, sent2)
    } else {
      listOf(sent1)
    }

    val projections = bubbles.map {
      Projection.relativeToViewWithCommonRoot(it.bubble, colorizerView, Projection.Corners(ViewUtil.dpToPx(10).toFloat()))
    }

    colorizerView.setProjections(projections)
    colorizerView.visibility = View.VISIBLE
    colorizerView.background = mask

    sentBubbles.forEach {
      it.body.setTextColor(colorizer.getOutgoingBodyTextColor(context))
      it.time.setTextColor(colorizer.getOutgoingFooterTextColor(context))
      it.delivery?.setTint(colorizer.getOutgoingFooterIconColor(context))
    }
  }

  private class Bubble(val bubble: View, val body: TextView, val time: TextView, val delivery: DeliveryStatusView?)
}
