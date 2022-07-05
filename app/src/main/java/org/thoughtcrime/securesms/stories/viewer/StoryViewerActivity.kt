package org.thoughtcrime.securesms.stories.viewer

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.media.AudioManagerCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.MemoryCategory
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.stories.StoryViewerArgs
import org.thoughtcrime.securesms.util.ServiceUtil
import kotlin.math.max
import kotlin.math.min

class StoryViewerActivity : PassphraseRequiredActivity(), VoiceNoteMediaControllerOwner {

  private val viewModel: StoryVolumeViewModel by viewModels()

  override lateinit var voiceNoteMediaController: VoiceNoteMediaController

  override fun attachBaseContext(newBase: Context) {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    super.attachBaseContext(newBase)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    StoryMutePolicy.initialize()
    Glide.get(this).setMemoryCategory(MemoryCategory.HIGH)

    supportPostponeEnterTransition()

    super.onCreate(savedInstanceState, ready)
    setContentView(R.layout.fragment_container)

    voiceNoteMediaController = VoiceNoteMediaController(this)

    if (savedInstanceState == null) {
      replaceStoryViewerFragment()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    Glide.get(this).setMemoryCategory(MemoryCategory.NORMAL)
  }

  override fun onResume() {
    super.onResume()
    if (StoryMutePolicy.isContentMuted) {
      viewModel.mute()
    } else {
      viewModel.unmute()
    }
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    setIntent(intent)
    replaceStoryViewerFragment()
  }

  override fun onEnterAnimationComplete() {
    if (Build.VERSION.SDK_INT >= 21) {
      window.transitionBackgroundFadeDuration = 100
    }
  }

  private fun replaceStoryViewerFragment() {
    supportFragmentManager.beginTransaction()
      .replace(
        R.id.fragment_container,
        StoryViewerFragment.create(intent.getParcelableExtra(ARGS)!!)
      )
      .commit()
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    val audioManager = ServiceUtil.getAudioManager(this)
    when (keyCode) {
      KeyEvent.KEYCODE_VOLUME_UP -> {
        val maxVolume = AudioManagerCompat.getStreamMaxVolume(audioManager, AudioManager.STREAM_MUSIC)
        StoryMutePolicy.isContentMuted = false
        viewModel.onVolumeUp(min(maxVolume, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + 1))
        return true
      }
      KeyEvent.KEYCODE_VOLUME_DOWN -> {
        val minVolume = AudioManagerCompat.getStreamMinVolume(audioManager, AudioManager.STREAM_MUSIC)
        viewModel.onVolumeDown(max(minVolume, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) - 1))
        return true
      }
    }

    return super.onKeyDown(keyCode, event)
  }

  companion object {
    private const val ARGS = "story.viewer.args"

    @JvmStatic
    fun createIntent(
      context: Context,
      storyViewerArgs: StoryViewerArgs
    ): Intent {
      return Intent(context, StoryViewerActivity::class.java).putExtra(ARGS, storyViewerArgs)
    }
  }
}
