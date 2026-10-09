/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediaviewer

import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.transition.Fade
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ShareCompat
import androidx.core.transition.addListener
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.logging.Log
import org.signal.mediaviewer.MediaViewerAction
import org.signal.mediaviewer.MediaViewerEvent
import org.signal.mediaviewer.MediaViewerScreen
import org.signal.mediaviewer.MediaViewerState
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.AttachmentSaver
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory
import org.thoughtcrime.securesms.mediapreview.MediaPreviewActivity
import org.thoughtcrime.securesms.mediapreview.MediaPreviewCache
import org.thoughtcrime.securesms.mediapreview.RecycledBitmapGuardDrawable
import org.thoughtcrime.securesms.mediasend.MediaSendLauncher
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.sharing.v2.ShareActivity
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.SaveAttachmentUtil
import org.thoughtcrime.securesms.util.WindowUtil

/**
 * Compose re-implementation of [MediaPreviewActivity], gated by [RemoteConfig.useNewMediaViewer]. Hosts
 * [MediaViewerScreen] and owns everything window-level: the shared element transition, the HDR
 * window color mode, and system bar visibility.
 */
class MediaViewerActivity : PassphraseRequiredActivity(), VoiceNoteMediaControllerOwner {

  override lateinit var voiceNoteMediaController: VoiceNoteMediaController

  private val args by lazy { MediaIntentFactory.requireArguments(intent.extras!!) }
  private val viewModel: MediaViewerViewModel by viewModels { MediaViewerViewModel.Factory(args) }

  private lateinit var transitionImageView: ImageView

  private var hdrHeadroomAnimator: ValueAnimator? = null
  private var currentHdrHeadroom: Float = SDR_HDR_HEADROOM

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    if (MediaPreviewCache.drawable != null && !args.skipSharedElementTransition) {
      val originalCorners = ShapeAppearanceModel.Builder()
        .setTopLeftCornerSize(args.sharedElementArgs.topLeft)
        .setTopRightCornerSize(args.sharedElementArgs.topRight)
        .setBottomRightCornerSize(args.sharedElementArgs.bottomRight)
        .setBottomLeftCornerSize(args.sharedElementArgs.bottomLeft)
        .build()

      val fullBleedCorners = ShapeAppearanceModel.builder().setAllCornerSizes(0f).build()

      setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
      window.sharedElementsUseOverlay = false
      window.sharedElementEnterTransition = buildContainerTransform(startShape = originalCorners, endShape = fullBleedCorners, isEnter = true)
      // Unlike the enter transform, the return transform must not hide the pager, which keeps the media on screen during the fade.
      window.sharedElementReturnTransition = buildContainerTransform(startShape = fullBleedCorners, endShape = originalCorners, isEnter = false)

      window.enterTransition = Fade().apply {
        duration = TRANSITION_DURATION_MS
        interpolator = TRANSITION_INTERPOLATOR
      }
    }

    super.onCreate(savedInstanceState, ready)
    setContentView(R.layout.media_viewer_activity)

    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (Build.VERSION.SDK_INT >= 28) {
      window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    transitionImageView = findViewById(R.id.transition_image_view)
    initializeTransitionImage()

    if (Build.VERSION.SDK_INT >= 35) {
      initializeWindowColorMode()
      collectHdrChanges()
    }

    voiceNoteMediaController = VoiceNoteMediaController(this, false)

    WindowUtil.setLightStatusBarFromTheme(this)
    WindowUtil.setLightNavigationBarFromTheme(this)

    findViewById<ComposeView>(R.id.compose_view).setContent {
      SignalTheme {
        val state by viewModel.state.collectAsStateWithLifecycle()

        CollectActions(viewModel.actions) { handleAction(it) }

        LaunchedEffect(state.chromeVisible) {
          val controller = WindowInsetsControllerCompat(window, window.decorView)
          controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
          if (state.chromeVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
          } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
          }
        }

        MediaViewerScreen(
          state = state,
          onEvent = viewModel::onEvent
        )
      }
    }
  }

  private fun buildContainerTransform(startShape: ShapeAppearanceModel, endShape: ShapeAppearanceModel, isEnter: Boolean): MaterialContainerTransform {
    return MaterialContainerTransform().apply {
      addTarget(MediaPreviewActivity.SHARED_ELEMENT_TRANSITION_NAME)
      drawingViewId = R.id.transition_container
      startShapeAppearanceModel = startShape
      endShapeAppearanceModel = endShape
      duration = TRANSITION_DURATION_MS
      interpolator = TRANSITION_INTERPOLATOR
      scrimColor = Color.TRANSPARENT
      addListener(
        onStart = {
          transitionImageView.alpha = 1f
          if (isEnter) {
            viewModel.onEvent(MediaViewerEvent.SharedAnimationChanged(true))
          }
        },
        onEnd = {
          if (isEnter) {
            viewModel.onEvent(MediaViewerEvent.SharedAnimationChanged(false))
          }
        }
      )
    }
  }

  private fun initializeTransitionImage() {
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

      lifecycleScope.launch {
        var hasMediaBeenReady = false
        viewModel.state
          .map { it.isInSharedAnimation to it.loadState }
          .distinctUntilChanged()
          .collect { (isInSharedAnimation, loadState) ->
            if (loadState == MediaViewerState.LoadState.MEDIA_READY) {
              hasMediaBeenReady = true
            }
            if (!isInSharedAnimation && hasMediaBeenReady) {
              transitionImageView.clearAnimation()
              transitionImageView.animate()
                .setInterpolator(TRANSITION_INTERPOLATOR)
                .setDuration(TRANSITION_IMAGE_FADE_DURATION_MS)
                .alpha(0f)
            }
          }
      }
    } else {
      transitionImageView.visibility = View.INVISIBLE
      viewModel.onEvent(MediaViewerEvent.SharedAnimationChanged(false))
    }
  }

  /**
   * The window commits to an HDR color mode up front, while it has yet to draw a frame: changing
   * the color mode on a visible window rebuilds its surface, which flashes. HDR is then
   * enabled and disabled per page purely via the desired headroom.
   */
  @RequiresApi(35)
  private fun initializeWindowColorMode() {
    if (!isDisplayHdrCapable()) {
      return
    }
    window.colorMode = ActivityInfo.COLOR_MODE_HDR
    window.desiredHdrHeadroom = SDR_HDR_HEADROOM
  }

  @RequiresApi(35)
  private fun collectHdrChanges() {
    if (window.colorMode != ActivityInfo.COLOR_MODE_HDR) {
      return
    }
    lifecycleScope.launch {
      viewModel.state
        .map { it.shouldRenderHdr }
        .distinctUntilChanged()
        .collect { wantHdr ->
          Log.i(TAG, "Changing desired HDR headroom. hdr=$wantHdr")
          animateHdrHeadroom(if (wantHdr) MIXED_CONTENT_HDR_HEADROOM else SDR_HDR_HEADROOM)
        }
    }
  }

  /**
   * Eases headroom changes in after the crossfade settles. A step change makes the compositor remap the
   * whole window at once, which reads as a brightness flash.
   */
  @RequiresApi(35)
  private fun animateHdrHeadroom(target: Float) {
    hdrHeadroomAnimator?.cancel()
    hdrHeadroomAnimator = ValueAnimator.ofFloat(currentHdrHeadroom, target).apply {
      val rampingUp = target > currentHdrHeadroom
      startDelay = if (rampingUp) HDR_RAMP_DELAY_MS else 0L
      duration = if (rampingUp) HDR_RAMP_UP_DURATION_MS else HDR_RAMP_DOWN_DURATION_MS
      addUpdateListener {
        currentHdrHeadroom = it.animatedValue as Float
        window.desiredHdrHeadroom = currentHdrHeadroom
      }
      start()
    }
  }

  private fun handleAction(action: MediaViewerAction) {
    when (action) {
      MediaViewerAction.Finish -> {
        finish()
      }
      MediaViewerAction.FinishAfterTransition -> {
        onBackPressedDispatcher.onBackPressed()
      }
      is MediaViewerAction.ShowToast -> {
        Toast.makeText(this, action.messageResId, Toast.LENGTH_LONG).show()
      }
      is MediaViewerAction.ShareMedia -> {
        share(action.uri, action.contentType)
      }
      is MediaViewerAction.ShowForwardSheet -> {
        MultiselectForwardFragmentArgs.create(this, action.threadId, action.uri, action.contentType) { forwardArgs ->
          MultiselectForwardFragment.showBottomSheet(supportFragmentManager, forwardArgs)
        }
      }
      is MediaViewerAction.SaveMedia -> {
        val saveAttachment = SaveAttachmentUtil.SaveAttachment(
          uri = action.uri,
          contentType = action.contentType,
          date = action.date,
          fileName = null
        )
        lifecycleScope.launch {
          AttachmentSaver(AttachmentSaver.ActivityHost(this@MediaViewerActivity)).saveAttachments(setOf(saveAttachment))
        }
      }
      is MediaViewerAction.OpenEditor -> {
        startActivity(MediaSendLauncher.editor(context = this, media = listOf(action.media)))
      }
      is MediaViewerAction.JumpToMessage -> {
        startActivity(action.intent)
        finish()
      }
      MediaViewerAction.PauseVoiceNotes -> {
        voiceNoteMediaController.pausePlayback()
      }
      MediaViewerAction.OpenStorageSettings -> {
        startActivity(AppSettingsActivity.manageStorage(this))
      }
    }
  }

  private fun share(uri: Uri, contentType: String?) {
    val publicUri = PartAuthority.getAttachmentPublicUri(uri)
    val mimeType = Intent.normalizeMimeType(contentType)
    val shareIntent = ShareCompat.IntentBuilder(this)
      .setStream(publicUri)
      .setType(mimeType)
      .createChooserIntent()
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    if (Build.VERSION.SDK_INT < 34) {
      shareIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(this, ShareActivity::class.java)))
    }

    try {
      startActivity(shareIntent)
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "No activity existed to share the media.", e)
      Toast.makeText(this, R.string.MediaPreviewActivity_cant_find_an_app_able_to_share_this_media, Toast.LENGTH_LONG).show()
    }
  }

  override fun onPause() {
    super.onPause()
    MediaPreviewCache.drawable = null
  }

  override fun onDestroy() {
    hdrHeadroomAnimator?.cancel()
    super.onDestroy()
  }

  override fun finishAfterTransition() {
    if (viewModel.shouldFinishAfterTransition()) {
      super.finishAfterTransition()
    } else {
      super.finish()
    }
  }

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
    private val TAG = Log.tag(MediaViewerActivity::class)

    private const val TRANSITION_DURATION_MS = 250L
    private const val TRANSITION_IMAGE_FADE_DURATION_MS = 200L
    private val TRANSITION_INTERPOLATOR = PathInterpolatorCompat.create(0.17f, 0.17f, 0f, 1f)

    /** Google's published "mixed content, mostly HDR" headroom. The viewer keeps SDR chrome in the same window. */
    private const val MIXED_CONTENT_HDR_HEADROOM = 3f

    /** A headroom ratio of 1 clamps rendering to SDR white, so SDR pages look identical to a default color mode window. */
    private const val SDR_HDR_HEADROOM = 1f

    /** Holds HDR back until the media crossfade ([TRANSITION_IMAGE_FADE_DURATION_MS]) has finished. */
    private const val HDR_RAMP_DELAY_MS = 300L

    private const val HDR_RAMP_UP_DURATION_MS = 800L

    /** Dropping to SDR happens on page changes, so it needs to keep up with a swipe. */
    private const val HDR_RAMP_DOWN_DURATION_MS = 250L
  }
}
