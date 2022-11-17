package org.thoughtcrime.securesms.mediapreview

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.google.android.exoplayer2.ui.PlayerControlView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.visible
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * The bottom bar for the media preview. This includes the standard seek bar as well as playback controls,
 * but adds forward and share buttons as well as a recyclerview that can be populated with a rail of thumbnails.
 */
class MediaPreviewPlayerControlView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
  playbackAttrs: AttributeSet? = null
) : PlayerControlView(context, attrs, defStyleAttr, playbackAttrs) {

  val recyclerView: RecyclerView = findViewById(R.id.media_preview_album_rail)
  private val durationBar: LinearLayout = findViewById(R.id.exo_duration_viewgroup)
  private val videoControls: LinearLayout = findViewById(R.id.exo_button_viewgroup)
  private val durationLabel: TextView = findViewById(R.id.exo_duration)
  private val shareButton: ImageButton = findViewById(R.id.exo_share)
  private val forwardButton: ImageButton = findViewById(R.id.exo_forward)

  enum class MediaMode {
    IMAGE, VIDEO;

    companion object {
      @JvmStatic
      fun fromString(contentType: String): MediaMode {
        if (MediaUtil.isVideo(contentType)) return VIDEO
        if (MediaUtil.isImageType(contentType)) return IMAGE
        throw IllegalArgumentException("Unknown content type: $contentType")
      }
    }
  }

  init {
    setShowPreviousButton(false)
    setShowNextButton(false)
    showShuffleButton = false
    showVrButton = false
    showTimeoutMs = -1
  }

  @SuppressLint("SetTextI18n")
  fun setMediaMode(mediaMode: MediaMode) {
    durationBar.visible = mediaMode == MediaMode.VIDEO
    videoControls.visibility = if (mediaMode == MediaMode.VIDEO) VISIBLE else INVISIBLE
    if (mediaMode == MediaMode.VIDEO) {
      setProgressUpdateListener { position, _ ->
        val finalPlayer = player ?: return@setProgressUpdateListener
        val remainingDuration = (finalPlayer.duration - position).toDuration(DurationUnit.MILLISECONDS)
        val minutes: Long = remainingDuration.inWholeMinutes
        val seconds: Long = remainingDuration.inWholeSeconds % 60
        durationLabel.text = "–${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
      }
    } else {
      setProgressUpdateListener(null)
    }
  }

  fun setShareButtonListener(listener: OnClickListener?) = shareButton.setOnClickListener(listener)

  fun setForwardButtonListener(listener: OnClickListener?) = forwardButton.setOnClickListener(listener)
}

class LottieAnimatedButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : LottieAnimationView(context, attrs) {

  init {
    addValueCallback(KeyPath("**"), LottieProperty.COLOR) { ContextCompat.getColor(context, R.color.signal_colorOnSurface) }
  }

  override fun onTouchEvent(event: MotionEvent?): Boolean {
    when (event?.action) {
      MotionEvent.ACTION_DOWN -> {
        speed = 1f
        playAnimation()
      }
      MotionEvent.ACTION_UP -> {
        if (isAnimating) {
          addAnimatorListener(object : AnimatorListener {
            override fun onAnimationEnd(animation: Animator?) {
              removeAllAnimatorListeners()
              playAnimationReverse()
            }

            override fun onAnimationStart(animation: Animator?) {}
            override fun onAnimationCancel(animation: Animator?) {}
            override fun onAnimationRepeat(animation: Animator?) {}
          })
        } else {
          playAnimationReverse()
        }
      }
    }
    return super.onTouchEvent(event)
  }

  private fun playAnimationReverse() {
    speed = -1f
    playAnimation()
  }
}
