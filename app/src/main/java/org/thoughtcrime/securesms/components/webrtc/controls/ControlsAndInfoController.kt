/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.controls

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Handler
import android.os.Parcelable
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
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
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.parcelize.Parcelize
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
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.service.webrtc.links.UpdateCallLinkResult
import org.thoughtcrime.securesms.util.padding
import org.thoughtcrime.securesms.util.visible
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

/**
 * Brain for rendering the call controls and info within a bottom sheet when we display the activity in portrait mode.
 */
class ControlsAndInfoController private constructor(
  private val webRtcCallActivity: WebRtcCallActivity,
  private val webRtcCallView: WebRtcCallView,
  private val overflowPopupWindow: CallOverflowPopupWindow,
  private val viewModel: WebRtcCallViewModel,
  private val controlsAndInfoViewModel: ControlsAndInfoViewModel,
  private val disposables: CompositeDisposable
) : CallInfoView.Callbacks, Disposable by disposables {

  constructor(
    webRtcCallActivity: WebRtcCallActivity,
    webRtcCallView: WebRtcCallView,
    overflowPopupWindow: CallOverflowPopupWindow,
    viewModel: WebRtcCallViewModel,
    controlsAndInfoViewModel: ControlsAndInfoViewModel
  ) : this(
    webRtcCallActivity,
    webRtcCallView,
    overflowPopupWindow,
    viewModel,
    controlsAndInfoViewModel,
    CompositeDisposable()
  )

  companion object {
    private val TAG = Log.tag(ControlsAndInfoController::class.java)

    private const val CONTROL_TRANSITION_DURATION = 250L
    private const val CONTROL_FADE_OUT_START = 0f
    private const val CONTROL_FADE_OUT_DONE = 0.23f
    private const val INFO_FADE_IN_START = CONTROL_FADE_OUT_DONE
    private const val INFO_FADE_IN_DONE = 0.8f
    private val INFO_TRANSLATION_DISTANCE = 24f.dp
    private val HIDE_CONTROL_DELAY = 5.seconds.inWholeMilliseconds
  }

  private val coordinator: CoordinatorLayout = webRtcCallView.findViewById(R.id.call_controls_info_coordinator)
  private val callInfoComposeView: ComposeView = webRtcCallView.findViewById(R.id.call_info_compose)
  private val frame: FrameLayout = webRtcCallView.findViewById(R.id.call_controls_info_parent)
  private val behavior = BottomSheetBehavior.from(frame)
  private val raiseHandComposeView: ComposeView = webRtcCallView.findViewById(R.id.call_screen_raise_hand_view)
  private val aboveControlsGuideline: Guideline = webRtcCallView.findViewById(R.id.call_screen_above_controls_guideline)
  private val toggleCameraDirectionView: View = webRtcCallView.findViewById(R.id.call_screen_camera_direction_toggle)
  private val callControls: ConstraintLayout = webRtcCallView.findViewById(R.id.call_controls_constraint_layout)
  private val isLandscape = webRtcCallActivity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
  private val waitingToBeLetInProgressDrawable = IndeterminateDrawable.createCircularDrawable(
    webRtcCallActivity,
    CircularProgressIndicatorSpec(webRtcCallActivity, null).apply {
      indicatorSize = 20.dp
      indicatorInset = 0.dp
      trackThickness = 2.dp
      trackCornerRadius = 1.dp
      indicatorColors = intArrayOf(ContextCompat.getColor(webRtcCallActivity, R.color.signal_colorOnBackground))
      trackColor = Color.TRANSPARENT
    }
  )
  private val waitingToBeLetIn: TextView = webRtcCallView.findViewById<TextView>(R.id.call_controls_waiting_to_be_let_in).apply {
    setCompoundDrawablesRelativeWithIntrinsicBounds(waitingToBeLetInProgressDrawable, null, null, null)
  }

  private val scheduleHideControlsRunnable: Runnable = Runnable { onScheduledHide() }
  private val bottomSheetVisibilityListeners = mutableSetOf<BottomSheetVisibilityListener>()

  private val handler: Handler?
    get() = webRtcCallView.handler

  private var previousCallControlHeightData = HeightData()
  private var controlState: WebRtcControls = WebRtcControls.NONE

  init {
    raiseHandComposeView.apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        RaiseHandSnackbar.View(viewModel, showCallInfoListener = ::showCallInfo)
      }
    }

    coordinator.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
      webRtcCallView.post { onControlTopChanged() }
    }

    raiseHandComposeView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
      onControlTopChanged()
    }

    val maxBehaviorHeightPercentage = if (isLandscape) 1f else 0.66f
    val minFrameHeightDenominator = if (isLandscape) 1 else 2

    callControls.viewTreeObserver.addOnGlobalLayoutListener {
      if (callControls.height > 0 && previousCallControlHeightData.hasChanged(callControls.height, coordinator.height)) {
        previousCallControlHeightData = HeightData(callControls.height, coordinator.height)

        val controlPeakHeight = callControls.height + callControls.y.toInt() + 16.dp
        behavior.peekHeight = controlPeakHeight
        frame.minimumHeight = coordinator.height / minFrameHeightDenominator
        behavior.maxHeight = (coordinator.height.toFloat() * maxBehaviorHeightPercentage).toInt()

        webRtcCallView.post { onControlTopChanged() }
      }
    }

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
        updateCallSheetVisibilities(slideOffset)
      }
    })

    callInfoComposeView.apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        val nestedScrollInterop = rememberNestedScrollInteropConnection()
        CallInfoView.View(viewModel, controlsAndInfoViewModel, this@ControlsAndInfoController, Modifier.nestedScroll(nestedScrollInterop))
      }
    }

    callInfoComposeView.alpha = 0f
    callInfoComposeView.translationY = INFO_TRANSLATION_DISTANCE
  }

  fun addVisibilityListener(listener: BottomSheetVisibilityListener): Boolean {
    return bottomSheetVisibilityListeners.add(listener)
  }

  fun onStateRestored() {
    when (behavior.state) {
      BottomSheetBehavior.STATE_EXPANDED -> {
        showCallInfo()
        updateCallSheetVisibilities(1f)
      }
      BottomSheetBehavior.STATE_HIDDEN -> {
        hide()
        updateCallSheetVisibilities(-1f)
      }
      else -> {
        showControls()
        updateCallSheetVisibilities(0f)
      }
    }
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
      if (controlState.isFadeOutEnabled || controlState == WebRtcControls.PIP || controlState.displayErrorControls()) {
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

  private fun updateCallSheetVisibilities(slideOffset: Float) {
    callControls.alpha = alphaControls(slideOffset)
    callControls.visible = callControls.alpha > 0f

    callInfoComposeView.alpha = alphaCallInfo(slideOffset)
    callInfoComposeView.translationY = INFO_TRANSLATION_DISTANCE - (INFO_TRANSLATION_DISTANCE * callInfoComposeView.alpha)
  }

  private fun onControlTopChanged() {
    val guidelineTop = max(frame.top, coordinator.height - behavior.peekHeight)
    aboveControlsGuideline.setGuidelineBegin(guidelineTop)
    webRtcCallView.onControlTopChanged()
  }

  private fun showOrHideControlsOnUpdate(previousState: WebRtcControls) {
    if (controlState == WebRtcControls.PIP || controlState.displayErrorControls()) {
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
      setControlConstraints(R.id.call_screen_video_toggle, controlState.displayVideoToggle(), margin)
      setControlConstraints(R.id.call_screen_audio_mic_toggle, controlState.displayMuteAudio(), margin)
      setControlConstraints(R.id.call_screen_audio_ring_toggle, controlState.displayRingToggle(), margin)
      setControlConstraints(R.id.call_screen_overflow_button, controlState.displayOverflow(), margin)
      setControlConstraints(R.id.call_screen_end_call, controlState.displayEndCall(), margin)
    }

    constraints.applyTo(callControls)

    toggleCameraDirectionView.visible = controlState.displayCameraToggle()
    waitingToBeLetIn.visible = controlState.displayWaitingToBeLetIn()

    if (controlState.displayWaitingToBeLetIn()) {
      waitingToBeLetInProgressDrawable.setVisible(true, false)
    } else {
      waitingToBeLetInProgressDrawable.stop()
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
        AppDependencies.signalCallManager.removeFromCallLink(callParticipant)
      }
      .setNeutralButton(R.string.CallLinkInfoSheet__block_from_call) { _, _ ->
        AppDependencies.signalCallManager.blockFromCallLink(callParticipant)
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
      displayEndCall() != previousState.displayEndCall() ||
      displayWaitingToBeLetIn() != previousState.displayWaitingToBeLetIn()
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

  @Parcelize
  private data class HeightData(
    val controlHeight: Int = 0,
    val coordinatorHeight: Int = 0
  ) : Parcelable {
    fun hasChanged(controlHeight: Int, coordinatorHeight: Int): Boolean {
      return controlHeight != this.controlHeight || coordinatorHeight != this.coordinatorHeight
    }
  }

  interface BottomSheetVisibilityListener {
    fun onShown()
    fun onHidden()
  }
}
