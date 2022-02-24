package org.thoughtcrime.securesms.stories.viewer.reply.composer

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.addListener
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiImageView
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.visible

class StoryReactionBar @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

  var callback: Callback? = null

  private var animatorSet: AnimatorSet? = null

  private val emojiVerticalTranslation = context.resources.getDimensionPixelSize(R.dimen.reaction_scrubber_anim_start_translation_y)

  init {
    inflate(context, R.layout.stories_reaction_bar, this)
  }

  private val background: View = findViewById(R.id.conversation_reaction_scrubber_background)
  private val emojiViews: List<EmojiImageView> = listOf(
    findViewById(R.id.reaction_1),
    findViewById(R.id.reaction_2),
    findViewById(R.id.reaction_3),
    findViewById(R.id.reaction_4),
    findViewById(R.id.reaction_5),
    findViewById(R.id.reaction_6),
    findViewById(R.id.reaction_7)
  )

  init {
    if (!isInEditMode) {
      val emojis = SignalStore.emojiValues().reactions
      emojiViews.forEachIndexed { index, emojiImageView ->
        if (index == emojiViews.lastIndex) {
          emojiImageView.setImageResource(R.drawable.ic_any_emoji_32)
          emojiImageView.setOnClickListener { onOpenReactionPicker() }
        } else {
          val emoji = SignalStore.emojiValues().getPreferredVariation(emojis[index])
          emojiImageView.setImageEmoji(emoji)
          emojiImageView.setOnClickListener { onEmojiSelected(emoji) }
        }
      }
    }
  }

  @SuppressLint("Recycle")
  fun show() {
    visible = true

    animatorSet?.cancel()
    animatorSet = AnimatorSet().apply {

      playTogether(
        emojiViews.flatMap {
          listOf(ObjectAnimator.ofFloat(it, View.ALPHA, 1f), ObjectAnimator.ofFloat(it, View.TRANSLATION_Y, 0f))
        } + ObjectAnimator.ofFloat(background, View.ALPHA, 1f)
      )

      start()
    }
  }

  private fun onEmojiSelected(emoji: String) {
    // TODO [stories] -- Animation / Haptics
    hide()
    callback?.onReactionSelected(emoji)
  }

  private fun onOpenReactionPicker() {
    // TODO [stories] -- Animation / Haptics
    hide()
    callback?.onOpenReactionPicker()
  }

  @SuppressLint("Recycle")
  private fun hide() {
    animatorSet?.cancel()
    animatorSet = AnimatorSet().apply {

      playTogether(
        emojiViews.flatMap {
          listOf(
            ObjectAnimator.ofFloat(it, View.ALPHA, 0f),
            ObjectAnimator.ofFloat(it, View.TRANSLATION_Y, emojiVerticalTranslation.toFloat())
          )
        } + ObjectAnimator.ofFloat(background, View.ALPHA, 0f)
      )

      addListener(onEnd = {
        visible = false
      })
      start()
    }
  }

  interface Callback {
    fun onReactionSelected(emoji: String)
    fun onOpenReactionPicker()
  }

  companion object {
    fun installIntoBottomSheet(context: Context, dialog: Dialog): StoryReactionBar {
      val container: ViewGroup = dialog.findViewById(R.id.container)

      val oldReactionBar: StoryReactionBar? = container.findViewById(R.id.reaction_bar)
      if (oldReactionBar != null) {
        return oldReactionBar
      }

      val reactionBar = StoryReactionBar(context)

      reactionBar.id = R.id.reaction_bar

      container.addView(reactionBar)
      return reactionBar
    }
  }
}
