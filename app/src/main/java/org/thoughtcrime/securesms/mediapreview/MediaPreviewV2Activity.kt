package org.thoughtcrime.securesms.mediapreview

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.commit
import org.thoughtcrime.securesms.R

class MediaPreviewV2Activity : AppCompatActivity(R.layout.activity_mediapreview_v2) {

  override fun attachBaseContext(newBase: Context) {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    super.attachBaseContext(newBase)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setTheme(R.style.TextSecure_MediaPreview)
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
