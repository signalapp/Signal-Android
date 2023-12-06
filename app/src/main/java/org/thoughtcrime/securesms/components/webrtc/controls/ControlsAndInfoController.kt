/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.controls

import android.content.res.ColorStateList
import android.os.Handler
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.bottomsheet.BottomSheetBehaviorHack
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import org.signal.core.util.dp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.InsetAwareConstraintLayout
import org.thoughtcrime.securesms.components.webrtc.WebRtcCallView
import org.thoughtcrime.securesms.components.webrtc.WebRtcCallViewModel
import org.thoughtcrime.securesms.components.webrtc.WebRtcControls
import org.thoughtcrime.securesms.util.padding
import org.thoughtcrime.securesms.util.visible
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

/**
 * Brain for rendering the call controls and info within a bottom sheet.
 */
class ControlsAndInfoController(
  private val webRtcCallView: WebRtcCallView,
  private val viewModel: WebRtcCallViewModel
) : Disposable {

  companion object {
    private const val CONTROL_FADE_OUT_START = 0f
    private const val CONTROL_FADE_OUT_DONE = 0.23f
    private const val INFO_FADE_IN_START = CONTROL_FADE_OUT_DONE
    private const val INFO_FADE_IN_DONE = 0.8f

    private val HIDE_CONTROL_DELAY = 5.seconds.inWholeMilliseconds
  }

  private val disposables = CompositeDisposable()

  private val coordinator: CoordinatorLayout
  private val frame: FrameLayout
  private val behavior: BottomSheetBehavior<View>
  private val callInfoComposeView: ComposeView
  private val callControls: ConstraintLayout
  private val bottomSheetVisibilityListeners = mutableSetOf<BottomSheetVisibilityListener>()
  private val scheduleHideControlsRunnable: Runnable = Runnable { onScheduledHide() }
  private val handler: Handler?
    get() = webRtcCallView.handler

  private var previousCallControlHeight = 0
  private var controlPeakHeight = 0
  private var controlState: WebRtcControls = WebRtcControls.NONE

  init {
    val infoTranslationDistance = 24f.dp
    coordinator = webRtcCallView.findViewById(R.id.call_controls_info_coordinator)
    frame = webRtcCallView.findViewById(R.id.call_controls_info_parent)
    behavior = BottomSheetBehavior.from(frame)
    callInfoComposeView = webRtcCallView.findViewById(R.id.call_info_compose)
    callControls = webRtcCallView.findViewById(R.id.call_controls_constraint_layout)

    callInfoComposeView.apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        val nestedScrollInterop = rememberNestedScrollInteropConnection()
        CallInfoView.View(viewModel, Modifier.nestedScroll(nestedScrollInterop))
      }
    }

    callInfoComposeView.alpha = 0f
    callInfoComposeView.translationY = infoTranslationDistance

    frame.background = MaterialShapeDrawable(
      ShapeAppearanceModel.builder()
        .setTopLeftCorner(CornerFamily.ROUNDED, 18.dp.toFloat())
        .setTopRightCorner(CornerFamily.ROUNDED, 18.dp.toFloat())
        .build()
    ).apply {
      fillColor = ColorStateList.valueOf(ContextCompat.getColor(webRtcCallView.context, R.color.signal_colorSurface))
    }

    behavior.isHideable = true
    behavior.peekHeight = 0
    behavior.state = BottomSheetBehavior.STATE_HIDDEN
    BottomSheetBehaviorHack.setNestedScrollingChild(behavior, callInfoComposeView)

    coordinator.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
      val guidelineTop = max(frame.top, (bottom - top) - behavior.peekHeight)
      webRtcCallView.post { webRtcCallView.onControlTopChanged(guidelineTop) }
    }

    callControls.viewTreeObserver.addOnGlobalLayoutListener {
      if (callControls.height > 0 && callControls.height != previousCallControlHeight) {
        previousCallControlHeight = callControls.height
        controlPeakHeight = callControls.height + callControls.y.toInt()
        behavior.peekHeight = controlPeakHeight
        frame.minimumHeight = coordinator.height / 2
        behavior.maxHeight = (coordinator.height.toFloat() * 0.66f).toInt()

        val guidelineTop = max(frame.top, coordinator.height - behavior.peekHeight)
        webRtcCallView.post { webRtcCallView.onControlTopChanged(guidelineTop) }
      }
    }

    behavior.addBottomSheetCallback(object : BottomSheetCallback() {
      override fun onStateChanged(bottomSheet: View, newState: Int) {
        if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
          if (controlState.isFadeOutEnabled) {
            hide(delay = HIDE_CONTROL_DELAY)
          }
        } else if (newState == BottomSheetBehavior.STATE_EXPANDED || newState == BottomSheetBehavior.STATE_DRAGGING) {
          cancelScheduledHide()
        }
      }

      override fun onSlide(bottomSheet: View, slideOffset: Float) {
        callControls.alpha = alphaControls(slideOffset)
        callControls.visible = callControls.alpha > 0f

        callInfoComposeView.alpha = alphaCallInfo(slideOffset)
        callInfoComposeView.translationY = infoTranslationDistance - (infoTranslationDistance * callInfoComposeView.alpha)

        webRtcCallView.onControlTopChanged(max(frame.top, coordinator.height - behavior.peekHeight))
      }
    })

    webRtcCallView.addWindowInsetsListener(object : InsetAwareConstraintLayout.WindowInsetsListener {
      override fun onApplyWindowInsets(statusBar: Int, navigationBar: Int, parentStart: Int, parentEnd: Int) {
        if (navigationBar > 0) {
          callControls.padding(bottom = navigationBar)
          callInfoComposeView.padding(bottom = navigationBar)
        }
      }
    })
  }

  fun addVisibilityListener(listener: BottomSheetVisibilityListener): Boolean {
    return bottomSheetVisibilityListeners.add(listener)
  }

  fun showCallInfo() {
    cancelScheduledHide()
    behavior.isHideable = false
    behavior.state = BottomSheetBehavior.STATE_EXPANDED
  }

  fun showControls() {
    cancelScheduledHide()
    behavior.isHideable = false
    behavior.state = BottomSheetBehavior.STATE_COLLAPSED

    bottomSheetVisibilityListeners.forEach { it.onShown() }
  }

  private fun hide(delay: Long = 0L) {
    if (delay == 0L) {
      if (controlState.isFadeOutEnabled || controlState == WebRtcControls.PIP) {
        behavior.isHideable = true
        behavior.state = BottomSheetBehavior.STATE_HIDDEN

        bottomSheetVisibilityListeners.forEach { it.onHidden() }
      }
    } else {
      cancelScheduledHide()
      handler?.postDelayed(scheduleHideControlsRunnable, delay)
    }
  }

  fun toggleControls() {
    if (behavior.state == BottomSheetBehavior.STATE_EXPANDED || behavior.state == BottomSheetBehavior.STATE_HIDDEN) {
      showControls()
    } else {
      hide()
    }
  }

  fun updateControls(newControlState: WebRtcControls) {
    val previousState = controlState
    controlState = newControlState

    if (controlState == WebRtcControls.PIP) {
      hide()
      return
    }

    if (controlState.hideControlsSheetInitially()) {
      return
    }

    if (previousState.hideControlsSheetInitially() && (previousState != WebRtcControls.PIP)) {
      showControls()
      return
    }

    if (controlState.isFadeOutEnabled) {
      if (!previousState.isFadeOutEnabled) {
        hide(delay = HIDE_CONTROL_DELAY)
      }
    } else {
      cancelScheduledHide()
      if (behavior.state != BottomSheetBehavior.STATE_EXPANDED) {
        showControls()
      }
    }
  }

  private fun onScheduledHide() {
    if (behavior.state != BottomSheetBehavior.STATE_EXPANDED && !isDisposed) {
      hide()
    }
  }

  private fun cancelScheduledHide() {
    handler?.removeCallbacks(scheduleHideControlsRunnable)
  }

  private fun alphaControls(slideOffset: Float): Float {
    return if (slideOffset <= CONTROL_FADE_OUT_START) {
      1f
    } else if (slideOffset >= CONTROL_FADE_OUT_DONE) {
      0f
    } else {
      1f - (1f * (slideOffset - CONTROL_FADE_OUT_START) / (CONTROL_FADE_OUT_DONE - CONTROL_FADE_OUT_START))
    }
  }

  private fun alphaCallInfo(slideOffset: Float): Float {
    return if (slideOffset >= INFO_FADE_IN_DONE) {
      1f
    } else if (slideOffset <= INFO_FADE_IN_START) {
      0f
    } else {
      (1f * (slideOffset - INFO_FADE_IN_START) / (INFO_FADE_IN_DONE - INFO_FADE_IN_START))
    }
  }

  override fun dispose() {
    disposables.dispose()
  }

  override fun isDisposed(): Boolean {
    return disposables.isDisposed
  }

  interface BottomSheetVisibilityListener {
    fun onShown()
    fun onHidden()
  }
}
