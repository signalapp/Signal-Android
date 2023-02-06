package org.thoughtcrime.securesms.stories.viewer.reply.reaction

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.constraintlayout.motion.widget.TransitionAdapter
import androidx.core.view.doOnNextLayout
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiImageView

class OnReactionSentView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

  var callback: Callback? = null

  init {
    inflate(context, R.layout.on_reaction_sent_view, this)
  }

  private val motionLayout: MotionLayout = findViewById(R.id.motion_layout)

  init {
    motionLayout.addTransitionListener(object : TransitionAdapter() {
      override fun onTransitionCompleted(p0: MotionLayout?, p1: Int) {
        motionLayout.progress = 0f
        callback?.onFinished()
      }
    })
  }

  fun playForEmoji(emoji: CharSequence) {
    motionLayout.progress = 0f

    listOf(
      R.id.emoji_1,
      R.id.emoji_2,
      R.id.emoji_3,
      R.id.emoji_4,
      R.id.emoji_5,
      R.id.emoji_6,
      R.id.emoji_7,
      R.id.emoji_8,
      R.id.emoji_9,
      R.id.emoji_10,
      R.id.emoji_11
    ).forEach {
      findViewById<EmojiImageView>(it).setImageEmoji(emoji)
    }

    motionLayout.requestLayout()
    motionLayout.doOnNextLayout {
      motionLayout.transitionToEnd()
    }
  }

  interface Callback {
    fun onFinished()
  }
}
