package org.thoughtcrime.securesms.mediapreview

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import org.thoughtcrime.securesms.R

class MediaPreviewV2Activity : AppCompatActivity(R.layout.activity_mediapreview_v2) {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
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
    private const val NOT_IN_A_THREAD = -2

    const val THREAD_ID_EXTRA = "thread_id"
  }
}
