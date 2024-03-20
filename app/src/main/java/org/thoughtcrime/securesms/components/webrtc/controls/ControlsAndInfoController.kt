/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.controls

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Handler
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.annotation.Px
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.Guideline
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.bottomsheet.BottomSheetBehaviorHack
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.dp
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.WebRtcCallActivity
import org.thoughtcrime.securesms.calls.links.CallLinks
import org.thoughtcrime.securesms.calls.links.EditCallLinkNameDialogFragment
import org.thoughtcrime.securesms.calls.links.EditCallLinkNameDialogFragmentArgs
import org.thoughtcrime.securesms.components.InsetAwareConstraintLayout
import org.thoughtcrime.securesms.components.webrtc.CallOverflowPopupWindow
import org.thoughtcrime.securesms.components.webrtc.WebRtcCallView
import org.thoughtcrime.securesms.components.webrtc.WebRtcCallViewModel
import org.thoughtcrime.securesms.components.webrtc.WebRtcControls
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.service.webrtc.links.UpdateCallLinkResult
import org.thoughtcrime.securesms.util.padding
import org.thoughtcrime.securesms.util.visible
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

/**
 * Brain for rendering the call controls and info within a bottom sheet.
 */
class ControlsAndInfoController(
  private val webRtcCallActivity: WebRtcCallActivity,
  private val webRtcCallView: WebRtcCallView,
  private val overflowPopupWindow: CallOverflowPopupWindow,
  private val viewModel: WebRtcCallViewModel,
  private val controlsAndInfoViewModel: ControlsAndInfoViewModel
) : CallInfoView.Callbacks, Disposable {

  companion object {
    private val TAG = Log.tag(ControlsAndInfoController::class.java)

    private const val CONTROL_FADE_OUT_START = 0f
    private const val CONTROL_FADE_OUT_DONE = 0.23f
    private const val INFO_FADE_IN_START = CONTROL_FADE_OUT_DONE
    private const val INFO_FADE_IN_DONE = 0.8f
    private const val CONTROL_TRANSITION_DURATION = 250L

    private val HIDE_CONTROL_DELAY = 5.seconds.inWholeMilliseconds
  }

  private val disposables = CompositeDisposable()

  private val coordinator: CoordinatorLayout
  private val frame: FrameLayout
  private val behavior: BottomSheetBehavior<View>
  private val callInfoComposeView: ComposeView
  private val raiseHandComposeView: ComposeView
  private val callControls: ConstraintLayout
  private val aboveControlsGuideline: Guideline
  private val bottomSheetVisibilityListeners = mutableSetOf<BottomSheetVisibilityListener>()
  private val scheduleHideControlsRunnable: Runnable = Runnable { onScheduledHide() }
  private val handler: Handler?
    get() = webRtcCallView.handler

  private var previousCallControlHeightData = HeightData()
  private var controlPeakHeight = 0
  private var controlState: WebRtcControls = WebRtcControls.NONE

  init {
    val infoTranslationDistance = 24f.dp
    coordinator = webRtcCallView.findViewById(R.id.call_controls_info_coordinator)
    frame = webRtcCallView.findViewById(R.id.call_controls_info_parent)
    behavior = BottomSheetBehavior.from(frame)
    callInfoComposeView = webRtcCallView.findViewById(R.id.call_info_compose)
    callControls = webRtcCallView.findViewById(R.id.call_controls_constraint_layout)
    raiseHandComposeView = webRtcCallView.findViewById(R.id.call_screen_raise_hand_view)
    aboveControlsGuideline = webRtcCallView.findViewById(R.id.call_screen_above_controls_guideline)

    callInfoComposeView.apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        val nestedScrollInterop = rememberNestedScrollInteropConnection()
        CallInfoView.View(viewModel, controlsAndInfoViewModel, this@ControlsAndInfoController, Modifier.nestedScroll(nestedScrollInterop))
      }
    }

    callInfoComposeView.alpha = 0f
    callInfoComposeView.translationY = infoTranslationDistance

    raiseHandComposeView.apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        RaiseHandSnackbar.View(viewModel, showCallInfoListener = ::showCallInfo)
      }
    }

    frame.background = MaterialShapeDrawable(
      ShapeAppearanceModel.builder()
        .setTopLeftCorner(CornerFamily.ROUNDED, 18.dp.toFloat())
        .setTopRightCorner(CornerFamily.ROUNDED, 18.dp.toFloat())
        .build()
    ).apply {
      fillColor = ColorStateList.valueOf(ContextCompat.getColor(webRtcCallActivity, R.color.signal_colorSurface))
    }

    behavior.isHideable = true
    behavior.peekHeight = 0
    behavior.state = BottomSheetBehavior.STATE_HIDDEN
    BottomSheetBehaviorHack.setNestedScrollingChild(behavior, callInfoComposeView)

    coordinator.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
      webRtcCallView.post { onControlTopChanged() }
    }

    raiseHandComposeView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
      onControlTopChanged()
    }

    callControls.viewTreeObserver.addOnGlobalLayoutListener {
      if (callControls.height > 0 && previousCallControlHeightData.hasChanged(callControls.height, coordinator.height)) {
        previousCallControlHeightData = HeightData(callControls.height, coordinator.height)

        controlPeakHeight = callControls.height + callControls.y.toInt()
        behavior.peekHeight = controlPeakHeight
        frame.minimumHeight = coordinator.height / 2
        behavior.maxHeight = (coordinator.height.toFloat() * 0.66f).toInt()

        webRtcCallView.post { onControlTopChanged() }
      }
    }

    behavior.addBottomSheetCallback(object : BottomSheetCallback() {
      override fun onStateChanged(bottomSheet: View, newState: Int) {
        overflowPopupWindow.dismiss()
        if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
          controlsAndInfoViewModel.resetScrollState()
          if (controlState.isFadeOutEnabled) {
            hide(delay = HIDE_CONTROL_DELAY)
          }
        } else if (newState == BottomSheetBehavior.STATE_EXPANDED || newState == BottomSheetBehavior.STATE_DRAGGING) {
          cancelScheduledHide()
        } else if (newState == BottomSheetBehavior.STATE_HIDDEN) {
          controlsAndInfoViewModel.resetScrollState()
        }
      }

      override fun onSlide(bottomSheet: View, slideOffset: Float) {
        callControls.alpha = alphaControls(slideOffset)
        callControls.visible = callControls.alpha > 0f

        callInfoComposeView.alpha = alphaCallInfo(slideOffset)
        callInfoComposeView.translationY = infoTranslationDistance - (infoTranslationDistance * callInfoComposeView.alpha)
      }
    })

    webRtcCallView.addWindowInsetsListener(object : InsetAwareConstraintLayout.WindowInsetsListener {
      override fun onApplyWindowInsets(statusBar: Int, navigationBar: Int, parentStart: Int, parentEnd: Int) {
        if (navigationBar > 0) {
          callControls.padding(bottom = navigationBar)
        }
      }
    })

    overflowPopupWindow.setOnDismissListener {
      hide(delay = HIDE_CONTROL_DELAY)
    }

    webRtcCallActivity
      .supportFragmentManager
      .setFragmentResultListener(EditCallLinkNameDialogFragment.RESULT_KEY, webRtcCallActivity) { resultKey, bundle ->
        if (bundle.containsKey(resultKey)) {
          setName(bundle.getString(resultKey)!!)
        }
      }
  }

  fun onControlTopChanged() {
    val guidelineTop = max(frame.top, coordinator.height - behavior.peekHeight)
    aboveControlsGuideline.setGuidelineBegin(guidelineTop)
    webRtcCallView.onControlTopChanged()
  }

  fun addVisibilityListener(listener: BottomSheetVisibilityListener): Boolean {
    return bottomSheetVisibilityListeners.add(listener)
  }

  fun showCallInfo() {
    cancelScheduledHide()
    behavior.isHideable = false
    behavior.state = BottomSheetBehavior.STATE_EXPANDED
  }

  private fun showControls() {
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

  fun toggleOverflowPopup() {
    if (overflowPopupWindow.isShowing) {
      overflowPopupWindow.dismiss()
    } else {
      cancelScheduledHide()
      overflowPopupWindow.show(aboveControlsGuideline)
    }
  }

  fun updateControls(newControlState: WebRtcControls) {
    val previousState = controlState
    controlState = newControlState

    showOrHideControlsOnUpdate(previousState)

    if (controlState != WebRtcControls.PIP && controlState.controlVisibilitiesChanged(previousState)) {
      updateControlVisibilities()
    }
  }

  fun restartHideControlsTimer() {
    hide(delay = HIDE_CONTROL_DELAY)
  }

  private fun showOrHideControlsOnUpdate(previousState: WebRtcControls) {
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

  private fun updateControlVisibilities() {
    TransitionManager.endTransitions(callControls)
    TransitionManager.beginDelayedTransition(
      callControls,
      AutoTransition().apply {
        ordering = TransitionSet.ORDERING_TOGETHER
        duration = CONTROL_TRANSITION_DURATION
      }
    )

    val constraints = ConstraintSet().apply {
      clone(callControls)
      val margin = if (controlState.displaySmallCallButtons()) 4.dp else 8.dp

      setControlConstraints(R.id.call_screen_speaker_toggle, controlState.displayAudioToggle(), margin)
      setControlConstraints(R.id.call_screen_camera_direction_toggle, controlState.displayCameraToggle(), margin)
      setControlConstraints(R.id.call_screen_video_toggle, controlState.displayVideoToggle(), margin)
      setControlConstraints(R.id.call_screen_audio_mic_toggle, controlState.displayMuteAudio(), margin)
      setControlConstraints(R.id.call_screen_audio_ring_toggle, controlState.displayRingToggle(), margin)
      setControlConstraints(R.id.call_screen_overflow_button, controlState.displayOverflow(), margin)
      setControlConstraints(R.id.call_screen_end_call, controlState.displayEndCall(), margin)
    }

    constraints.applyTo(callControls)
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

  override fun onShareLinkClicked() {
    val mimeType = Intent.normalizeMimeType("text/plain")
    val shareIntent = ShareCompat.IntentBuilder(webRtcCallActivity)
      .setText(CallLinks.url(controlsAndInfoViewModel.rootKeySnapshot))
      .setType(mimeType)
      .createChooserIntent()

    try {
      webRtcCallActivity.startActivity(shareIntent)
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(webRtcCallActivity, R.string.CreateCallLinkBottomSheetDialogFragment__failed_to_open_share_sheet, Toast.LENGTH_LONG).show()
    }
  }

  override fun onEditNameClicked(name: String) {
    EditCallLinkNameDialogFragment().apply {
      arguments = EditCallLinkNameDialogFragmentArgs.Builder(name).build().toBundle()
    }.show(webRtcCallActivity.supportFragmentManager, null)
  }

  override fun onToggleAdminApprovalClicked(checked: Boolean) {
    controlsAndInfoViewModel.setApproveAllMembers(checked)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(onSuccess = {
        if (it !is UpdateCallLinkResult.Update) {
          Log.w(TAG, "Failed to change restrictions. $it")
          toastFailure()
        }
      }, onError = handleError("onApproveAllMembersChanged"))
      .addTo(disposables)
  }

  override fun onBlock(callParticipant: CallParticipant) {
    MaterialAlertDialogBuilder(webRtcCallActivity)
      .setNegativeButton(android.R.string.cancel, null)
      .setMessage(webRtcCallView.resources.getString(R.string.CallLinkInfoSheet__remove_s_from_the_call, callParticipant.recipient.getShortDisplayName(webRtcCallActivity)))
      .setPositiveButton(R.string.CallLinkInfoSheet__remove) { _, _ ->
        ApplicationDependencies.getSignalCallManager().removeFromCallLink(callParticipant)
      }
      .setNeutralButton(R.string.CallLinkInfoSheet__block_from_call) { _, _ ->
        ApplicationDependencies.getSignalCallManager().blockFromCallLink(callParticipant)
      }
      .show()
  }

  private fun setName(name: String) {
    controlsAndInfoViewModel.setName(name)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(
        onSuccess = {
          if (it !is UpdateCallLinkResult.Update) {
            Log.w(TAG, "Failed to set name. $it")
            toastFailure()
          }
        },
        onError = handleError("setName")
      )
      .addTo(disposables)
  }

  private fun handleError(method: String): (throwable: Throwable) -> Unit {
    return {
      Log.w(TAG, "Failure during $method", it)
      toastFailure()
    }
  }

  private fun toastFailure() {
    Toast.makeText(webRtcCallActivity, R.string.CallLinkDetailsFragment__couldnt_save_changes, Toast.LENGTH_LONG).show()
  }

  private fun ConstraintSet.setControlConstraints(@IdRes viewId: Int, visible: Boolean, @Px horizontalMargins: Int) {
    setVisibility(viewId, if (visible) View.VISIBLE else View.GONE)
    setMargin(viewId, ConstraintSet.START, horizontalMargins)
    setMargin(viewId, ConstraintSet.END, horizontalMargins)
  }

  private fun WebRtcControls.controlVisibilitiesChanged(previousState: WebRtcControls): Boolean {
    return displayAudioToggle() != previousState.displayAudioToggle() ||
      displayCameraToggle() != previousState.displayCameraToggle() ||
      displayVideoToggle() != previousState.displayVideoToggle() ||
      displayMuteAudio() != previousState.displayMuteAudio() ||
      displayRingToggle() != previousState.displayRingToggle() ||
      displayOverflow() != previousState.displayOverflow() ||
      displayEndCall() != previousState.displayEndCall()
  }

  private data class HeightData(
    val controlHeight: Int = 0,
    val coordinatorHeight: Int = 0
  ) {
    fun hasChanged(controlHeight: Int, coordinatorHeight: Int): Boolean {
      return controlHeight != this.controlHeight || coordinatorHeight != this.coordinatorHeight
    }
  }

  interface BottomSheetVisibilityListener {
    fun onShown()
    fun onHidden()
  }
}
