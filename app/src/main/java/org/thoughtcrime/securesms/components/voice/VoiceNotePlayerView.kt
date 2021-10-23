package org.thoughtcrime.securesms.components.voice

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.PlaybackSpeedToggleTextView
import org.thoughtcrime.securesms.recipients.RecipientId
import java.util.concurrent.TimeUnit

private const val ANIMATE_DURATION: Long = 150L
private const val TO_PAUSE = 1
private const val TO_PLAY = -1

/**
 * Renders a bar at the top of Conversation list and in a conversation to allow
 * playback manipulation of voice notes.
 */
class VoiceNotePlayerView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

  private val playPauseToggleView: LottieAnimationView
  private val infoView: TextView
  private val durationView: TextView
  private val speedView: PlaybackSpeedToggleTextView
  private val closeButton: View

  private var lastState: State? = null
  private var playerVisible: Boolean = false
  private var lottieDirection: Int = 0

  var listener: Listener? = null

  init {
    inflate(context, R.layout.voice_note_player_view, this)

    playPauseToggleView = findViewById(R.id.voice_note_player_play_pause_toggle)
    infoView = findViewById(R.id.voice_note_player_info)
    durationView = findViewById(R.id.voice_note_player_duration)
    speedView = findViewById(R.id.voice_note_player_speed)
    closeButton = findViewById(R.id.voice_note_player_close)

    infoView.isSelected = true

    val speedTouchTarget: View = findViewById(R.id.voice_note_player_speed_touch_target)
    speedTouchTarget.setOnClickListener {
      speedView.performClick()
    }

    speedView.playbackSpeedListener = object : PlaybackSpeedToggleTextView.PlaybackSpeedListener {
      override fun onPlaybackSpeedChanged(speed: Float) {
        lastState?.let {
          listener?.onSpeedChangeRequested(it.uri, speed)
        }
      }
    }

    closeButton.setOnClickListener {
      lastState?.let {
        listener?.onCloseRequested(it.uri)
      }
    }

    playPauseToggleView.setOnClickListener {
      lastState?.let {
        if (it.isPaused) {
          if (it.playbackPosition >= it.playbackDuration) {
            listener?.onPlay(it.uri, it.messageId, 0.0)
          } else {
            listener?.onPlay(it.uri, it.messageId, it.playbackPosition.toDouble() / it.playbackDuration)
          }
        } else {
          listener?.onPause(it.uri)
        }
      }
    }

    post {
      playPauseToggleView.addValueCallback(
        KeyPath("**"),
        LottieProperty.COLOR_FILTER,
        LottieValueCallback(SimpleColorFilter(ContextCompat.getColor(context, R.color.signal_icon_tint_primary)))
      )
    }

    if (background != null) {
      background.colorFilter = SimpleColorFilter(ContextCompat.getColor(context, R.color.voice_note_player_view_background))
    }

    contentDescription = context.getString(R.string.VoiceNotePlayerView__navigate_to_voice_message)
    setOnClickListener {
      lastState?.let {
        listener?.onNavigateToMessage(it.threadId, it.threadRecipientId, it.senderId, it.messageTimestamp, it.messagePositionInThread)
      }
    }
  }

  fun setState(state: State) {
    this.lastState = state

    if (state.isPaused) {
      animateToggleToPlay()
    } else {
      animateToggleToPause()
    }

    if (infoView.text != state.name) {
      infoView.text = state.name
    }

    durationView.text = context.getString(R.string.VoiceNotePlayerView__dot_s, formatDuration(state.playbackDuration - state.playbackPosition))
    speedView.setCurrentSpeed(state.playbackSpeed)
  }

  fun show() {
    if (!playerVisible) {
      visibility = VISIBLE

      val animation = AnimationUtils.loadAnimation(context, R.anim.slide_from_top)
      animation.duration = ANIMATE_DURATION

      startAnimation(animation)
    }

    playerVisible = true
  }

  fun hide() {
    if (playerVisible) {
      val animation = AnimationUtils.loadAnimation(context, R.anim.slide_to_top)
      animation.duration = ANIMATE_DURATION
      animation.setAnimationListener(object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation?) = Unit
        override fun onAnimationRepeat(animation: Animation?) = Unit

        override fun onAnimationEnd(animation: Animation?) {
          visibility = GONE
        }
      })

      startAnimation(animation)
    }

    playerVisible = false
  }

  private fun formatDuration(duration: Long): String {
    val secs = TimeUnit.MILLISECONDS.toSeconds(duration)

    return resources.getString(R.string.AudioView_duration, secs / 60, secs % 60)
  }

  private fun animateToggleToPlay() {
    startLottieAnimation(TO_PLAY)
  }

  private fun animateToggleToPause() {
    startLottieAnimation(TO_PAUSE)
  }

  private fun startLottieAnimation(direction: Int) {
    if (lottieDirection == direction) {
      return
    }

    lottieDirection = direction
    playPauseToggleView.contentDescription = if (direction == TO_PLAY) {
      context.getString(R.string.VoiceNotePlayerView__play_voice_message)
    } else {
      context.getString(R.string.VoiceNotePlayerView__pause_voice_message)
    }

    playPauseToggleView.pauseAnimation()
    playPauseToggleView.speed = (direction * 2).toFloat()
    playPauseToggleView.resumeAnimation()
  }

  data class State(
    val uri: Uri,
    val messageId: Long,
    val threadId: Long,
    val isPaused: Boolean,
    val senderId: RecipientId,
    val threadRecipientId: RecipientId,
    val messagePositionInThread: Long,
    val messageTimestamp: Long,
    val name: String,
    val playbackPosition: Long,
    val playbackDuration: Long,
    val playbackSpeed: Float
  )

  interface Listener {
    fun onPlay(uri: Uri, messageId: Long, position: Double)
    fun onPause(uri: Uri)
    fun onCloseRequested(uri: Uri)
    fun onSpeedChangeRequested(uri: Uri, speed: Float)
    fun onNavigateToMessage(threadId: Long, threadRecipientId: RecipientId, senderId: RecipientId, messageSentAt: Long, messagePositionInThread: Long)
  }
}
