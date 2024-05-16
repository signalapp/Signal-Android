package org.thoughtcrime.securesms.scribbles

import android.animation.Animator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import org.signal.core.util.logging.Log.tag
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.createDefaultCubicBezierInterpolator

/**
 * The play button overlay for controlling playback in the video editor.
 */
class VideoEditorPlayButtonLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {
  private val playOverlay: View = inflate(this.context, R.layout.video_editor_hud, this).findViewById(R.id.play_overlay)

  fun setPlayClickListener(listener: OnClickListener?) {
    playOverlay.setOnClickListener(listener)
  }

  fun showPlayButton() {
    playOverlay.visibility = VISIBLE
    playOverlay.animate()
      .setListener(null)
      .alpha(1f)
      .setInterpolator(createDefaultCubicBezierInterpolator())
      .setDuration(500)
      .start()
  }

  fun fadePlayButton() {
    playOverlay.animate()
      .setListener(object : Animator.AnimatorListener {
        override fun onAnimationEnd(animation: Animator) {
          playOverlay.visibility = GONE
        }
        override fun onAnimationStart(animation: Animator) = Unit
        override fun onAnimationCancel(animation: Animator) = Unit
        override fun onAnimationRepeat(animation: Animator) = Unit
      })
      .alpha(0f)
      .setInterpolator(createDefaultCubicBezierInterpolator())
      .setDuration(200)
      .start()
  }

  fun hidePlayButton() {
    playOverlay.visibility = GONE
    playOverlay.setAlpha(0f)
  }

  companion object {
    @Suppress("unused")
    private val TAG = tag(VideoEditorPlayButtonLayout::class.java)
  }
}
