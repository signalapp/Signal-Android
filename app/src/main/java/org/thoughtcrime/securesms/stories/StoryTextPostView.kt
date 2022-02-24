package org.thoughtcrime.securesms.stories

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.airbnb.lottie.SimpleColorFilter
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel
import org.thoughtcrime.securesms.mediasend.v2.text.TextAlignment
import org.thoughtcrime.securesms.mediasend.v2.text.TextStoryPostCreationState
import org.thoughtcrime.securesms.mediasend.v2.text.TextStoryScale
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible

class StoryTextPostView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

  init {
    inflate(context, R.layout.stories_text_post_view, this)
  }

  private var textAlignment: TextAlignment? = null
  private val backgroundView: ImageView = findViewById(R.id.text_story_post_background)
  private val textView: TextView = findViewById(R.id.text_story_post_text)
  private val linkPreviewView: StoryLinkPreviewView = findViewById(R.id.text_story_post_link_preview)

  private var isPlaceholder: Boolean = true

  init {
    backgroundView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
      textView.maxWidth = backgroundView.measuredWidth - DimensionUnit.DP.toPixels(40f).toInt()

      textAlignment?.apply {
        adjustTextTranslationX(this)
      }
    }

    textView.doAfterTextChanged {
      textAlignment?.apply {
        adjustTextTranslationX(this)
      }
    }
  }

  fun showCloseButton() {
    linkPreviewView.setCanClose(true)
  }

  fun hideCloseButton() {
    linkPreviewView.setCanClose(false)
  }

  fun setTypeface(typeface: Typeface) {
    textView.typeface = typeface
  }

  fun setPostBackground(drawable: Drawable) {
    backgroundView.setImageDrawable(drawable)
  }

  fun setTextColor(@ColorInt color: Int) {
    textView.setTextColor(color)
  }

  fun setText(text: CharSequence, isPlaceholder: Boolean) {
    this.isPlaceholder = isPlaceholder
    textView.text = text
  }

  fun setTextSize(@Px textSize: Float) {
    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
  }

  fun setTextGravity(textAlignment: TextAlignment) {
    textView.gravity = textAlignment.gravity
  }

  fun setTextScale(scalePercent: Int) {
    val scale = TextStoryScale.convertToScale(scalePercent)
    textView.scaleX = scale
    textView.scaleY = scale
  }

  fun setTextVisible(visible: Boolean) {
    textView.visible = visible
  }

  fun setTextBackgroundColor(@ColorInt color: Int) {
    if (color == Color.TRANSPARENT) {
      textView.background = null
    } else {
      textView.background = AppCompatResources.getDrawable(context, R.drawable.rounded_rectangle_secondary_18)?.apply {
        colorFilter = SimpleColorFilter(color)
      }
    }
  }

  fun bindFromCreationState(state: TextStoryPostCreationState) {
    textAlignment = state.textAlignment

    setPostBackground(state.backgroundColor.chatBubbleMask)
    setText(
      if (state.body.isEmpty()) {
        context.getString(R.string.TextStoryPostCreationFragment__tap_to_add_text)
      } else {
        state.body
      },
      state.body.isEmpty()
    )

    setTextColor(state.textForegroundColor)
    setTextSize(state.textSize)
    setTextBackgroundColor(state.textBackgroundColor)
    setTextGravity(state.textAlignment)
    setTextScale(state.textScale)

    postAdjustTextTranslationX(state.textAlignment)
    postAdjustLinkPreviewTranslationY()
  }

  fun bindLinkPreviewState(linkPreviewState: LinkPreviewViewModel.LinkPreviewState, hiddenVisibility: Int) {
    linkPreviewView.bind(linkPreviewState, hiddenVisibility)
  }

  fun postAdjustLinkPreviewTranslationY() {
    setTextVisible(canDisplayText())
    doOnNextLayout {
      adjustLinkPreviewTranslationY()
    }
  }

  fun postAdjustTextTranslationX(textAlignment: TextAlignment) {
    doOnNextLayout {
      adjustTextTranslationX(textAlignment)
    }
  }

  fun setTextViewClickListener(onClickListener: OnClickListener) {
    textView.setOnClickListener(onClickListener)
  }

  fun setLinkPreviewCloseListener(onClickListener: OnClickListener) {
    linkPreviewView.setOnCloseClickListener(onClickListener)
  }

  fun showPostContent() {
    textView.alpha = 1f
    linkPreviewView.alpha = 1f
  }

  fun hidePostContent() {
    textView.alpha = 0f
    linkPreviewView.alpha = 0f
  }

  private fun canDisplayText(): Boolean {
    return !(linkPreviewView.isVisible && isPlaceholder)
  }

  private fun adjustLinkPreviewTranslationY() {
    val backgroundHeight = backgroundView.measuredHeight
    val textHeight = if (canDisplayText()) textView.measuredHeight * textView.scaleY else 0f
    val previewHeight = if (linkPreviewView.visible) linkPreviewView.measuredHeight else 0
    val availableHeight = backgroundHeight - textHeight

    if (availableHeight >= previewHeight) {
      val totalContentHeight = textHeight + previewHeight
      val topAndBottomMargin = backgroundHeight - totalContentHeight
      val margin = topAndBottomMargin / 2f

      linkPreviewView.translationY = -margin

      val originPoint = textView.measuredHeight / 2f
      val desiredPoint = (textHeight / 2f) + margin

      textView.translationY = desiredPoint - originPoint
    } else {
      linkPreviewView.translationY = 0f

      val originPoint = textView.measuredHeight / 2f
      val desiredPoint = backgroundHeight / 2f

      textView.translationY = desiredPoint - originPoint
    }
  }

  private fun alignTextLeft() {
    textView.translationX = DimensionUnit.DP.toPixels(20f)
  }

  private fun alignTextRight() {
    textView.translationX = backgroundView.measuredWidth - textView.measuredWidth - DimensionUnit.DP.toPixels(20f)
  }

  private fun adjustTextTranslationX(textAlignment: TextAlignment) {
    when (textAlignment) {
      TextAlignment.CENTER -> {
        textView.translationX = backgroundView.measuredWidth / 2f - textView.measuredWidth / 2f
      }
      TextAlignment.START -> {
        if (ViewUtil.isLtr(textView)) {
          alignTextLeft()
        } else {
          alignTextRight()
        }
      }
      TextAlignment.END -> {
        if (ViewUtil.isRtl(textView)) {
          alignTextLeft()
        } else {
          alignTextRight()
        }
      }
    }
  }
}
