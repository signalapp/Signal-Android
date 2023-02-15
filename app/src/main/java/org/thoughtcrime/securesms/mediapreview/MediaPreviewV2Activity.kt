package org.thoughtcrime.securesms.mediapreview

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.transition.addListener
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.fragment.app.commit
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.util.LifecycleDisposable

class MediaPreviewV2Activity : PassphraseRequiredActivity(), VoiceNoteMediaControllerOwner {

  override lateinit var voiceNoteMediaController: VoiceNoteMediaController

  private val viewModel: MediaPreviewV2ViewModel by viewModels()
  private val lifecycleDisposable = LifecycleDisposable()

  private lateinit var transitionImageView: ImageView

  override fun attachBaseContext(newBase: Context) {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    super.attachBaseContext(newBase)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    val args = MediaIntentFactory.requireArguments(intent.extras!!)

    if (MediaPreviewCache.drawable != null) {
      val originalCorners = ShapeAppearanceModel.Builder()
        .setTopLeftCornerSize(args.sharedElementArgs.topLeft)
        .setTopRightCornerSize(args.sharedElementArgs.topRight)
        .setBottomRightCornerSize(args.sharedElementArgs.bottomRight)
        .setBottomLeftCornerSize(args.sharedElementArgs.bottomLeft)
        .build()

      setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
      window.sharedElementEnterTransition = MaterialContainerTransform().apply {
        addTarget(SHARED_ELEMENT_TRANSITION_NAME)
        startShapeAppearanceModel = originalCorners
        endShapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(0f).build()
        duration = 250L
        interpolator = PathInterpolatorCompat.create(0.17f, 0.17f, 0f, 1f)
        addListener(
          onStart = {
            transitionImageView.alpha = 1f
            viewModel.setIsInSharedAnimation(true)
          },
          onEnd = {
            viewModel.setIsInSharedAnimation(false)
          }
        )
      }

      window.sharedElementExitTransition = MaterialContainerTransform().apply {
        addTarget(SHARED_ELEMENT_TRANSITION_NAME)
        startShapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(0f).build()
        endShapeAppearanceModel = originalCorners
        duration = 250L
        interpolator = PathInterpolatorCompat.create(0.17f, 0.17f, 0f, 1f)
        addListener(
          onStart = {
            transitionImageView.alpha = 1f
            viewModel.setIsInSharedAnimation(true)
          },
          onEnd = {
            viewModel.setIsInSharedAnimation(false)
          }
        )
      }
    }

    super.onCreate(savedInstanceState, ready)
    setTheme(R.style.TextSecure_MediaPreview)
    setContentView(R.layout.activity_mediapreview_v2)

    transitionImageView = findViewById(R.id.transition_image_view)
    if (MediaPreviewCache.drawable != null) {
      transitionImageView.setImageDrawable(MediaPreviewCache.drawable)
    } else {
      transitionImageView.visibility = View.INVISIBLE
      viewModel.setIsInSharedAnimation(false)
    }

    lifecycleDisposable += viewModel.state.map {
      it.isInSharedAnimation to it.loadState
    }.distinctUntilChanged().subscribe { (isInSharedAnimation, loadState) ->
      if (!isInSharedAnimation && loadState == MediaPreviewV2State.LoadState.MEDIA_READY) {
        transitionImageView.clearAnimation()
        transitionImageView.animate().alpha(0f)
      }
    }

    voiceNoteMediaController = VoiceNoteMediaController(this)

    val systemBarColor = ContextCompat.getColor(this, R.color.signal_dark_colorSurface)
    window.statusBarColor = systemBarColor
    window.navigationBarColor = systemBarColor

    if (savedInstanceState == null) {
      val bundle = Bundle()
      bundle.putParcelable(MediaPreviewV2Fragment.ARGS_KEY, args)
      supportFragmentManager.commit {
        setReorderingAllowed(true)
        add(R.id.fragment_container_view, MediaPreviewV2Fragment::class.java, bundle, FRAGMENT_TAG)
      }
    }
  }

  override fun onPause() {
    super.onPause()
    MediaPreviewCache.drawable = null
  }

  companion object {
    private const val FRAGMENT_TAG = "media_preview_fragment_v2"
    const val SHARED_ELEMENT_TRANSITION_NAME = "thumb"
  }
}
