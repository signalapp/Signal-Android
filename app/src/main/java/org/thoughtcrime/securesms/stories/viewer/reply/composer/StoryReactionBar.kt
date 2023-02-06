package org.thoughtcrime.securesms.stories.viewer.reply.composer

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
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

  init {
    inflate(context, R.layout.stories_reaction_bar, this)
    alpha = 0f
    setBackgroundResource(R.drawable.conversation_reaction_overlay_background)
  }

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

    setOnClickListener {
      callback?.onTouchOutsideOfReactionBar()
    }
  }

  @SuppressLint("Recycle")
  fun animateIn() {
    visible = true

    animatorSet?.cancel()
    animatorSet = AnimatorSet().apply {
      playTogether(
        emojiViews.flatMap {
          listOf(ObjectAnimator.ofFloat(it, View.ALPHA, 1f), ObjectAnimator.ofFloat(it, View.TRANSLATION_Y, 0f))
        } + ObjectAnimator.ofFloat(this@StoryReactionBar, View.ALPHA, 1f)
      )

      start()
    }
  }

  private fun onEmojiSelected(emoji: String) {
    callback?.onReactionSelected(emoji)
  }

  private fun onOpenReactionPicker() {
    callback?.onOpenReactionPicker()
  }

  interface Callback {
    fun onTouchOutsideOfReactionBar()
    fun onReactionSelected(emoji: String)
    fun onOpenReactionPicker()
  }
}
