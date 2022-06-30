package org.thoughtcrime.securesms.stories

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.visible

/**
 * Displays a volume bar along with an indicator specifiying whether or not
 * a given video contains sound.
 */
class StoryVolumeOverlayView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

  init {
    inflate(context, R.layout.story_volume_overlay_view, this)
  }

  private val videoHasNoAudioIndicator: View = findViewById(R.id.story_no_audio_indicator)
  private val volumeBar: StoryVolumeBar = findViewById(R.id.story_volume_bar)

  fun setVideoHaNoAudio(videoHasNoAudio: Boolean) {
    videoHasNoAudioIndicator.visible = videoHasNoAudio
  }

  fun setVolumeLevel(level: Int) {
    volumeBar.setLevel(level)
  }
}
