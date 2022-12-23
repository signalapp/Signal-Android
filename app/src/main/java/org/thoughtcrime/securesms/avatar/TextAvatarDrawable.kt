package org.thoughtcrime.securesms.avatar

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withTranslation
import org.thoughtcrime.securesms.components.emoji.EmojiProvider

class TextAvatarDrawable(
  private val context: Context,
  private val avatar: Avatar.Text,
  inverted: Boolean = false,
  private val size: Int = AvatarRenderer.DIMENSIONS,
  private val synchronous: Boolean = false
) : Drawable() {

  private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
  init {
    textPaint.typeface = AvatarRenderer.getTypeface(context)
    textPaint.color = if (inverted) avatar.color.backgroundColor else avatar.color.foregroundColor
    textPaint.density = context.resources.displayMetrics.density

    setBounds(0, 0, size, size)
  }

  @Suppress("DEPRECATION")
  override fun draw(canvas: Canvas) {
    val width = bounds.width()
    val textSize = Avatars.getTextSizeForLength(context, avatar.text, width * 0.8f, width * 0.45f)
    val candidates = EmojiProvider.getCandidates(avatar.text)

    textPaint.textSize = textSize

    val newText = if (candidates == null || candidates.size() == 0) {
      SpannableString(avatar.text)
    } else {
      EmojiProvider.emojify(context, candidates, avatar.text, textPaint, synchronous, true)
    }

    if (newText == null) return

    val layout = StaticLayout(SpannableString(newText), textPaint, width, Layout.Alignment.ALIGN_NORMAL, 0f, 0f, true)
    layout.draw(canvas, getStartX(layout), ((bounds.height() / 2) - ((layout.height / 2))).toFloat())
  }

  private fun getStartX(layout: StaticLayout): Float {
    val direction = layout.getParagraphDirection(0)
    val lineWidth = layout.getLineWidth(0)
    val width = bounds.width()
    val xPos = (width - lineWidth) / 2
    return if (direction == Layout.DIR_LEFT_TO_RIGHT) xPos else -xPos
  }

  override fun setAlpha(alpha: Int) = Unit

  override fun setColorFilter(colorFilter: ColorFilter?) = Unit

  override fun getOpacity(): Int = PixelFormat.OPAQUE

  private fun Layout.draw(canvas: Canvas, x: Float, y: Float) {
    canvas.withTranslation(x, y) {
      draw(canvas)
    }
  }
}
