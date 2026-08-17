package org.thoughtcrime.securesms.mediapreview

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.transition.addListener
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.commit
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.util.WindowUtil
import java.util.concurrent.TimeUnit

class MediaPreviewActivity : PassphraseRequiredActivity(), VoiceNoteMediaControllerOwner {

  override lateinit var voiceNoteMediaController: VoiceNoteMediaController

  private val viewModel: MediaPreviewViewModel by viewModels()
  private val lifecycleDisposable = LifecycleDisposable()
  private val args by lazy {
    MediaIntentFactory.requireArguments(intent.extras!!)
  }

  private lateinit var transitionImageView: ImageView

  private var isWindowStarted = false

  override fun attachBaseContext(newBase: Context) {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    super.attachBaseContext(newBase)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    if (MediaPreviewCache.drawable != null && !args.skipSharedElementTransition) {
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
    lifecycleDisposable.bindTo(this)
    setTheme(R.style.TextSecure_MediaPreview)
    setContentView(R.layout.activity_media_preview)

    transitionImageView = findViewById(R.id.transition_image_view)
    val cacheDrawable = MediaPreviewCache.drawable?.let { RecycledBitmapGuardDrawable(it) }
    if (cacheDrawable != null && !args.skipSharedElementTransition) {
      val bounds = cacheDrawable.bounds
      val aspectRatio = bounds.width().toFloat() / bounds.height()
      val screenRatio = resources.displayMetrics.widthPixels.toFloat() / resources.displayMetrics.heightPixels
      if (aspectRatio > screenRatio) {
        transitionImageView.updateLayoutParams<LayoutParams> {
          width = LayoutParams.MATCH_PARENT
        }
      } else {
        transitionImageView.updateLayoutParams<LayoutParams> {
          height = LayoutParams.MATCH_PARENT
        }
      }

      val originalCallback = cacheDrawable.callback
      transitionImageView.setImageDrawable(cacheDrawable)
      cacheDrawable.callback = originalCallback

      var hasMediaBeenReady = false
      lifecycleDisposable += viewModel.state.map {
        it.isInSharedAnimation to it.loadState
      }.distinctUntilChanged().subscribe { (isInSharedAnimation, loadState) ->
        if (loadState == MediaPreviewState.LoadState.MEDIA_READY) {
          hasMediaBeenReady = true
        }
        if (!isInSharedAnimation && hasMediaBeenReady) {
          transitionImageView.clearAnimation()
          transitionImageView.animate()
            .setInterpolator(PathInterpolatorCompat.create(0.17f, 0.17f, 0f, 1f))
            .setDuration(200)
            .alpha(0f)
        }
      }
    } else {
      transitionImageView.visibility = View.INVISIBLE
      viewModel.setIsInSharedAnimation(false)
    }

    if (Build.VERSION.SDK_INT >= 35) {
      // ViewPager2 reports the newly selected page as soon as the snap target is decided, i.e. while the
      // settle animation is still running and both pages are on screen. Writing the window color mode there
      // rebuilds the HWUI surface mid-scroll, so in a mixed HDR/SDR album a fast flick would rebuild it once
      // per page. Debouncing collapses a flick into a single write once the pager has come to rest.
      // A delayed write can never strand the window in HDR: onStop() and finishAfterTransition() both clear
      // synchronously, and applyWindowColorMode() refuses to raise HDR unless the window is started.
      lifecycleDisposable += viewModel.state
        .map { it.shouldRenderHdr }
        .distinctUntilChanged()
        .debounce(COLOR_MODE_SETTLE_DELAY_MS, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
        .subscribe { applyWindowColorMode(it) }
    }

    voiceNoteMediaController = VoiceNoteMediaController(this, false)

    WindowUtil.clearLightStatusBar(window)
    WindowUtil.clearLightNavigationBar(window)

    if (savedInstanceState == null) {
      val bundle = Bundle()
      bundle.putParcelable(MediaPreviewFragment.ARGS_KEY, args)
      supportFragmentManager.commit {
        setReorderingAllowed(true)
        add(R.id.fragment_container_view, MediaPreviewFragment::class.java, bundle, FRAGMENT_TAG)
      }
    }
  }

  override fun onStart() {
    super.onStart()
    isWindowStarted = true
    applyWindowColorMode(viewModel.shouldRenderHdr)
  }

  override fun onStop() {
    isWindowStarted = false
    applyWindowColorMode(false)
    super.onStop()
  }

  override fun onPause() {
    super.onPause()
    MediaPreviewCache.drawable = null
  }

  override fun finishAfterTransition() {
    applyWindowColorMode(false)
    if (viewModel.shouldFinishAfterTransition(args.initialMediaUri)) {
      super.finishAfterTransition()
    } else {
      super.finish()
    }
  }

  /**
   * Applies the desired window color mode. This activity is the sole writer of [android.view.Window.setColorMode];
   * HDR is only ever requested while the window is started and the display can actually present it.
   *
   * The API 35 floor is [android.view.Window.setDesiredHdrHeadroom]: below that the compositor picks the headroom
   * itself and visibly dims the SDR chrome (toolbar, caption, album rail) sharing this window, so raising HDR
   * would cost more than it buys. It matches the floor
   * [org.thoughtcrime.securesms.components.subsampling.UltraHdrSupport.isEligible] applies at decode time.
   */
  private fun applyWindowColorMode(wantHdr: Boolean) {
    if (Build.VERSION.SDK_INT < 35) {
      return
    }

    val target = if (wantHdr && isWindowStarted && isDisplayHdrCapable()) {
      ActivityInfo.COLOR_MODE_HDR
    } else {
      ActivityInfo.COLOR_MODE_DEFAULT
    }

    if (window.colorMode == target) {
      return
    }

    Log.i(TAG, "Changing window color mode. hdr=${target == ActivityInfo.COLOR_MODE_HDR}")
    window.colorMode = target
    window.desiredHdrHeadroom = if (target == ActivityInfo.COLOR_MODE_HDR) MIXED_CONTENT_HDR_HEADROOM else 0f
  }

  /** The API 34 floor is [android.view.Display.isHdrSdrRatioAvailable]'s own, not the preview's; callers are already above it. */
  @RequiresApi(34)
  private fun isDisplayHdrCapable(): Boolean {
    return try {
      display?.isHdrSdrRatioAvailable == true
    } catch (e: Exception) {
      Log.w(TAG, "Unable to query display HDR capability.", e)
      false
    }
  }

  companion object {
    private val TAG = Log.tag(MediaPreviewActivity::class)

    private const val FRAGMENT_TAG = "media_preview_fragment"
    const val SHARED_ELEMENT_TRANSITION_NAME = "thumb"

    /** Google's published "mixed content, mostly HDR" headroom. The preview keeps SDR chrome in the same window. */
    private const val MIXED_CONTENT_HDR_HEADROOM = 3f

    /** How long the page selection must stay put before we pay for a window color mode change. */
    private const val COLOR_MODE_SETTLE_DELAY_MS = 200L
  }
}
