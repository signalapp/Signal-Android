package org.thoughtcrime.securesms.stories

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import com.google.android.material.imageview.ShapeableImageView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost
import org.thoughtcrime.securesms.fonts.Fonts
import org.thoughtcrime.securesms.fonts.TextFont
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel
import org.thoughtcrime.securesms.mediasend.v2.text.TextAlignment
import org.thoughtcrime.securesms.mediasend.v2.text.TextStoryPostCreationState
import org.thoughtcrime.securesms.mediasend.v2.text.TextStoryScale
import org.thoughtcrime.securesms.mediasend.v2.text.TextStoryTextWatcher
import org.thoughtcrime.securesms.stories.viewer.page.StoryDisplay
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture
import org.thoughtcrime.securesms.util.concurrent.SimpleTask
import org.thoughtcrime.securesms.util.visible
import java.util.Locale

class StoryTextPostView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

  init {
    inflate(context, R.layout.stories_text_post_view, this)
  }

  private var textAlignment: TextAlignment? = null
  private val backgroundView: ShapeableImageView = findViewById(R.id.text_story_post_background)
  private val textView: StoryTextView = findViewById(R.id.text_story_post_text)
  private val linkPreviewView: StoryLinkPreviewView = findViewById(R.id.text_story_post_link_preview)

  private var isPlaceholder: Boolean = true

  init {
    TextStoryTextWatcher.install(textView)

    val displaySize = StoryDisplay.getStoryDisplay(
      resources.displayMetrics.widthPixels.toFloat(),
      resources.displayMetrics.heightPixels.toFloat()
    )

    when (displaySize) {
      StoryDisplay.SMALL ->
        backgroundView.shapeAppearanceModel = backgroundView.shapeAppearanceModel
          .toBuilder()
          .setAllCornerSizes(0f)
          .build()
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
    textView.setWrappedBackgroundColor(color)
  }

  fun bindFromCreationState(state: TextStoryPostCreationState) {
    textAlignment = state.textAlignment

    setPostBackground(state.backgroundColor.chatBubbleMask)
    setText(
      state.body.ifEmpty {
        context.getString(R.string.TextStoryPostCreationFragment__tap_to_add_text)
      }.let {
        if (state.textFont.isAllCaps) {
          it.toString().toUpperCase(Locale.getDefault())
        } else {
          it
        }
      },
      state.body.isEmpty()
    )

    setTextColor(state.textForegroundColor)
    setTextBackgroundColor(state.textBackgroundColor)
    setTextGravity(state.textAlignment)
    setTextScale(state.textScale)

    postAdjustLinkPreviewTranslationY()
  }

  fun bindFromStoryTextPost(storyTextPost: StoryTextPost) {
    linkPreviewView.visible = false

    textAlignment = TextAlignment.CENTER

    val font = TextFont.fromStyle(storyTextPost.style)
    setPostBackground(ChatColors.forChatColor(ChatColors.Id.NotSet, storyTextPost.background).chatBubbleMask)

    if (font.isAllCaps) {
      setText(storyTextPost.body.toUpperCase(Locale.getDefault()), false)
    } else {
      setText(storyTextPost.body, false)
    }

    setTextColor(storyTextPost.textForegroundColor)
    setTextBackgroundColor(storyTextPost.textBackgroundColor)
    setTextGravity(TextAlignment.CENTER)

    SimpleTask.run(
      {
        when (val fontResult = Fonts.resolveFont(context, Locale.getDefault(), font)) {
          is Fonts.FontResult.Immediate -> fontResult.typeface
          is Fonts.FontResult.Async -> fontResult.future.get()
        }
      },
      { typeface -> setTypeface(typeface) }
    )

    hideCloseButton()

    postAdjustLinkPreviewTranslationY()
  }

  fun bindLinkPreview(linkPreview: LinkPreview?): ListenableFuture<Boolean> {
    return linkPreviewView.bind(linkPreview, View.GONE)
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

  fun setTextViewClickListener(onClickListener: OnClickListener) {
    setOnClickListener(onClickListener)
  }

  fun setLinkPreviewCloseListener(onClickListener: OnClickListener) {
    linkPreviewView.setOnCloseClickListener(onClickListener)
  }

  fun setLinkPreviewClickListener(onClickListener: OnClickListener?) {
    linkPreviewView.setOnClickListener(onClickListener)
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
}
