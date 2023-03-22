package org.thoughtcrime.securesms.stories.viewer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.media.AudioManagerCompat
import androidx.window.layout.WindowMetricsCalculator
import com.bumptech.glide.Glide
import com.bumptech.glide.MemoryCategory
import org.signal.core.util.dp
import org.signal.core.util.getParcelableCompat
import org.signal.core.util.getParcelableExtraCompat
import org.signal.core.util.sp
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.stories.StoryViewerArgs
import org.thoughtcrime.securesms.stories.viewer.page.StoryViewStateCache
import org.thoughtcrime.securesms.stories.viewer.page.StoryViewStateViewModel
import org.thoughtcrime.securesms.util.FullscreenHelper
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.ViewUtil
import kotlin.math.max
import kotlin.math.min

class StoryViewerActivity : PassphraseRequiredActivity(), VoiceNoteMediaControllerOwner {

  private val viewModel: StoryVolumeViewModel by viewModels()
  private val storyViewStateViewModel: StoryViewStateViewModel by viewModels()

  val ringerModeReceiver = RingerModeReceiver()

  override lateinit var voiceNoteMediaController: VoiceNoteMediaController

  override fun attachBaseContext(newBase: Context) {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    super.attachBaseContext(newBase)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    if (savedInstanceState != null) {
      val cache: StoryViewStateCache? = savedInstanceState.getParcelableCompat(DATA_CACHE, StoryViewStateCache::class.java)
      if (cache != null) {
        storyViewStateViewModel.storyViewStateCache.putAll(cache)
      }
    }

    StoryMutePolicy.initialize()
    Glide.get(this).setMemoryCategory(MemoryCategory.HIGH)
    FullscreenHelper.showSystemUI(window)

    supportPostponeEnterTransition()

    val root = findViewById<View>(android.R.id.content)
    val metrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(this)
    val statusBarHeight = ViewUtil.getStatusBarHeight(root)
    val contentHeight = metrics.bounds.width() * (16 / 9f) + 48.sp
    val spaceAndNavbar = metrics.bounds.height() - statusBarHeight - contentHeight
    val (padTop, padBottom) = if (spaceAndNavbar > 72.dp) {
      val pad = (metrics.bounds.height() - contentHeight) / 2f
      pad to pad
    } else {
      statusBarHeight to ViewUtil.getNavigationBarHeight(root)
    }

    root.setPadding(
      0,
      padTop.toInt(),
      0,
      padBottom.toInt()
    )

    super.onCreate(savedInstanceState, ready)
    setContentView(R.layout.fragment_container)

    voiceNoteMediaController = VoiceNoteMediaController(this)

    if (savedInstanceState == null) {
      replaceStoryViewerFragment()
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putParcelable(DATA_CACHE, storyViewStateViewModel.storyViewStateCache)
  }

  override fun onPause() {
    super.onPause()
    unregisterReceiver(ringerModeReceiver)
  }

  override fun onDestroy() {
    super.onDestroy()
    Glide.get(this).setMemoryCategory(MemoryCategory.NORMAL)
  }

  override fun onResume() {
    super.onResume()
    registerReceiver(ringerModeReceiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION))
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
    window.transitionBackgroundFadeDuration = 100
  }

  private fun replaceStoryViewerFragment() {
    supportFragmentManager.beginTransaction()
      .replace(
        R.id.fragment_container,
        StoryViewerFragment.create(intent.getParcelableExtraCompat(ARGS, StoryViewerArgs::class.java)!!)
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

  inner class RingerModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      when (intent?.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_SILENT)) {
        AudioManager.RINGER_MODE_NORMAL -> {
          StoryMutePolicy.isContentMuted = false
          viewModel.unmute()
        }
        else -> {
          StoryMutePolicy.isContentMuted = true
          viewModel.mute()
        }
      }
    }
  }

  companion object {
    private const val ARGS = "story.viewer.args"
    private const val DATA_CACHE = "story.viewer.cache"

    @JvmStatic
    fun createIntent(
      context: Context,
      storyViewerArgs: StoryViewerArgs
    ): Intent {
      return Intent(context, StoryViewerActivity::class.java).putExtra(ARGS, storyViewerArgs)
    }
  }
}
