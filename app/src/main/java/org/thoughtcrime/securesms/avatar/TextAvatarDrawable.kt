package org.thoughtcrime.securesms.avatar

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import org.thoughtcrime.securesms.components.emoji.EmojiTextView

/**
 * Uses EmojiTextView to properly render a Text Avatar with emoji in it.
 */
class TextAvatarDrawable(
  context: Context,
  avatar: Avatar.Text,
  inverted: Boolean = false,
  private val size: Int = AvatarRenderer.DIMENSIONS,
) : Drawable() {

  private val layout: FrameLayout = FrameLayout(context)
  private val textView: EmojiTextView = EmojiTextView(context)

  init {
    textView.typeface = AvatarRenderer.getTypeface(context)
    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, Avatars.getTextSizeForLength(context, avatar.text, size * 0.8f, size * 0.45f))
    textView.text = avatar.text
    textView.gravity = Gravity.CENTER
    textView.setTextColor(if (inverted) avatar.color.backgroundColor else avatar.color.foregroundColor)
    textView.setForceCustomEmoji(true)

    layout.addView(textView)

    textView.updateLayoutParams {
      width = size
      height = size
    }

    layout.measure(size, size)
    layout.layout(0, 0, size, size)
  }

  override fun getIntrinsicHeight(): Int = size

  override fun getIntrinsicWidth(): Int = size

  override fun draw(canvas: Canvas) {
    layout.draw(canvas)
  }

  override fun setAlpha(alpha: Int) = Unit

  override fun setColorFilter(colorFilter: ColorFilter?) = Unit

  override fun getOpacity(): Int = PixelFormat.OPAQUE
}
