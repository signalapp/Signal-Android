package org.thoughtcrime.securesms.mediapreview

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.commit
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner

class MediaPreviewV2Activity : PassphraseRequiredActivity(), VoiceNoteMediaControllerOwner {

  override lateinit var voiceNoteMediaController: VoiceNoteMediaController

  override fun attachBaseContext(newBase: Context) {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    super.attachBaseContext(newBase)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)
    setTheme(R.style.TextSecure_MediaPreview)
    setContentView(R.layout.activity_mediapreview_v2)
    voiceNoteMediaController = VoiceNoteMediaController(this)

    if (Build.VERSION.SDK_INT >= 21) {
      val systemBarColor = ContextCompat.getColor(this, R.color.signal_dark_colorSurface)
      window.statusBarColor = systemBarColor
      window.navigationBarColor = systemBarColor
    }
    if (savedInstanceState == null) {
      val bundle = Bundle()
      val args = MediaIntentFactory.requireArguments(intent.extras!!)
      bundle.putParcelable(MediaPreviewV2Fragment.ARGS_KEY, args)
      supportFragmentManager.commit {
        setReorderingAllowed(true)
        add(R.id.fragment_container_view, MediaPreviewV2Fragment::class.java, bundle, FRAGMENT_TAG)
      }
    }
  }

  companion object {
    private const val FRAGMENT_TAG = "media_preview_fragment_v2"
  }
}
