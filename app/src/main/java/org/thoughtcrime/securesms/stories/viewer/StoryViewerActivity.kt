package org.thoughtcrime.securesms.stories.viewer

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.stories.StoryViewerArgs

class StoryViewerActivity : PassphraseRequiredActivity(), VoiceNoteMediaControllerOwner {

  override lateinit var voiceNoteMediaController: VoiceNoteMediaController

  override fun attachBaseContext(newBase: Context) {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    super.attachBaseContext(newBase)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    supportPostponeEnterTransition()

    super.onCreate(savedInstanceState, ready)
    setContentView(R.layout.fragment_container)

    voiceNoteMediaController = VoiceNoteMediaController(this)

    if (savedInstanceState == null) {
      replaceStoryViewerFragment()
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
