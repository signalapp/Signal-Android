package org.thoughtcrime.securesms.stories

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.database.model.databaseprotos.StoryTextPost
import org.thoughtcrime.securesms.fonts.TextFont
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel
import org.thoughtcrime.securesms.mediasend.v2.text.TextAlignment
import org.thoughtcrime.securesms.mediasend.v2.text.TextStoryPostCreationState
import org.thoughtcrime.securesms.mediasend.v2.text.TextStoryScale
import org.thoughtcrime.securesms.mediasend.v2.text.TextStoryTextWatcher
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture
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
  private val backgroundView: ImageView = findViewById(R.id.text_story_post_background)
  private val textView: StoryTextView = findViewById(R.id.text_story_post_text)
  private val linkPreviewView: StoryLinkPreviewView = findViewById(R.id.text_story_post_link_preview)

  private var isPlaceholder: Boolean = true

  init {
    TextStoryTextWatcher.install(textView)
  }

  fun getLinkPreviewThumbnailWidth(useLargeThumbnail: Boolean): Int {
    return linkPreviewView.getThumbnailViewWidth(useLargeThumbnail)
  }

  fun getLinkPreviewThumbnailHeight(useLargeThumbnail: Boolean): Int {
    return linkPreviewView.getThumbnailViewHeight(useLargeThumbnail)
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

  private fun setPostBackground(drawable: Drawable) {
    backgroundView.setImageDrawable(drawable)
  }

  private fun setTextColor(@ColorInt color: Int, isPlaceholder: Boolean) {
    if (isPlaceholder) {
      textView.setTextColor(ColorUtils.setAlphaComponent(color, 0x99))
    } else {
      textView.setTextColor(color)
    }
  }

  private fun setText(text: CharSequence, isPlaceholder: Boolean) {
    this.isPlaceholder = isPlaceholder
    textView.text = text
  }

  private fun setTextGravity(textAlignment: TextAlignment) {
    textView.gravity = textAlignment.gravity
  }

  private fun setTextScale(scalePercent: Int) {
    val scale = TextStoryScale.convertToScale(scalePercent)
    textView.scaleX = scale
    textView.scaleY = scale
  }

  private fun setTextVisible(visible: Boolean) {
    textView.visible = visible
  }

  private fun setTextBackgroundColor(@ColorInt color: Int) {
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
          it.toString().uppercase(Locale.getDefault())
        } else {
          it
        }
      },
      state.body.isEmpty()
    )

    setTextColor(state.textForegroundColor, state.body.isEmpty())
    setTextBackgroundColor(state.textBackgroundColor)
    setTextGravity(state.textAlignment)
    setTextScale(state.textScale)

    postAdjustLinkPreviewTranslationY()
  }

  fun bindFromStoryTextPost(storyTextPost: StoryTextPost) {
    visible = true
    linkPreviewView.visible = false

    textAlignment = TextAlignment.CENTER

    val font = TextFont.fromStyle(storyTextPost.style)
    setPostBackground(ChatColors.forChatColor(ChatColors.Id.NotSet, storyTextPost.background).chatBubbleMask)

    if (font.isAllCaps) {
      setText(storyTextPost.body.uppercase(Locale.getDefault()), false)
    } else {
      setText(storyTextPost.body, false)
    }

    setTextColor(storyTextPost.textForegroundColor, false)
    setTextBackgroundColor(storyTextPost.textBackgroundColor)
    setTextGravity(TextAlignment.CENTER)

    hideCloseButton()

    postAdjustLinkPreviewTranslationY()
  }

  fun bindLinkPreview(linkPreview: LinkPreview?, useLargeThumbnail: Boolean, loadThumbnail: Boolean = true): ListenableFuture<Boolean> {
    return linkPreviewView.bind(linkPreview, View.GONE, useLargeThumbnail, loadThumbnail)
  }

  fun setLinkPreviewDrawable(drawable: Drawable?, useLargeThumbnail: Boolean) {
    linkPreviewView.setThumbnailDrawable(drawable, useLargeThumbnail)
  }

  fun bindLinkPreviewState(linkPreviewState: LinkPreviewViewModel.LinkPreviewState, hiddenVisibility: Int, useLargeThumbnail: Boolean) {
    linkPreviewView.bind(linkPreviewState, hiddenVisibility, useLargeThumbnail)
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
    linkPreviewView.setOnPreviewClickListener(onClickListener)
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
    return !(linkPreviewView.isVisible && (isPlaceholder || textView.text.isEmpty()))
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
