/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.AllNightPreviews
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.TriggerAlignedPopupState
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.components.webrtc.WebRtcLocalRenderState
import org.thoughtcrime.securesms.components.webrtc.controls.RaiseHandSnackbar
import org.thoughtcrime.securesms.conversation.colors.ChatColorsPalette
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.events.CallParticipantId
import org.thoughtcrime.securesms.events.GroupCallRaiseHandEvent
import org.thoughtcrime.securesms.events.GroupCallReactionEvent
import org.thoughtcrime.securesms.events.WebRtcViewModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.ringrtc.CameraState
import kotlin.math.max
import kotlin.math.round
import kotlin.time.Duration.Companion.seconds

private const val DRAG_HANDLE_HEIGHT = 22
private const val SHEET_TOP_PADDING = 9
private const val SHEET_BOTTOM_PADDING = 16

/**
 * In-App calling screen displaying controls, info, and participant camera feeds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
  callRecipient: Recipient,
  webRtcCallState: WebRtcViewModel.State,
  isRemoteVideoOffer: Boolean,
  isInPipMode: Boolean,
  callScreenState: CallScreenState,
  callControlsState: CallControlsState,
  callScreenController: CallScreenController = CallScreenController.rememberCallScreenController(
    skipHiddenState = callControlsState.skipHiddenState,
    onControlsToggled = {},
    callControlsState = callControlsState,
    callControlsListener = CallScreenControlsListener.Empty
  ),
  callScreenControlsListener: CallScreenControlsListener = CallScreenControlsListener.Empty,
  callScreenSheetDisplayListener: CallScreenSheetDisplayListener = CallScreenSheetDisplayListener.Empty,
  additionalActionsListener: AdditionalActionsListener = AdditionalActionsListener.Empty,
  callParticipantsPagerState: CallParticipantsPagerState,
  pendingParticipantsListener: PendingParticipantsListener = PendingParticipantsListener.Empty,
  callParticipantUpdatePopupController: CallParticipantUpdatePopupController,
  overflowParticipants: List<CallParticipant>,
  localParticipant: CallParticipant,
  localRenderState: WebRtcLocalRenderState,
  callScreenDialogType: CallScreenDialogType,
  reactions: List<GroupCallReactionEvent>,
  callInfoView: @Composable (Float) -> Unit,
  raiseHandSnackbar: @Composable (Modifier) -> Unit,
  onNavigationClick: () -> Unit,
  onLocalPictureInPictureClicked: () -> Unit,
  onLocalPictureInPictureFocusClicked: () -> Unit,
  onControlsToggled: (Boolean) -> Unit,
  onCallScreenDialogDismissed: () -> Unit = {}
) {
  if (webRtcCallState == WebRtcViewModel.State.CALL_INCOMING) {
    IncomingCallScreen(
      callRecipient = callRecipient,
      isVideoCall = isRemoteVideoOffer,
      callStatus = callScreenState.callStatus,
      callScreenControlsListener = callScreenControlsListener
    )

    return
  }

  if (isInPipMode) {
    PictureInPictureCallScreen(
      callParticipantsPagerState = callParticipantsPagerState,
      callScreenController = callScreenController
    )

    return
  }

  var peekPercentage by remember {
    mutableFloatStateOf(0f)
  }

  val scaffoldState = remember(callScreenController) { callScreenController.scaffoldState }
  val scope = rememberCoroutineScope()
  val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

  val additionalActionsPopupState = TriggerAlignedPopupState.rememberTriggerAlignedPopupState()
  val additionalActionsState = remember(
    callScreenState.reactions,
    localParticipant.isHandRaised
  ) {
    AdditionalActionsState(
      reactions = callScreenState.reactions,
      isSelfHandRaised = localParticipant.isHandRaised,
      listener = additionalActionsListener,
      triggerAlignedPopupState = additionalActionsPopupState
    )
  }

  additionalActionsPopupState.display = callScreenState.displayAdditionalActionsDialog

  BoxWithConstraints {
    val maxHeight = constraints.maxHeight
    val maxSheetHeight = round(constraints.maxHeight * 0.66f)
    val maxOffset = maxHeight - maxSheetHeight

    var peekHeight by remember { mutableFloatStateOf(88f) }

    BottomSheetScaffold(
      scaffoldState = callScreenController.scaffoldState,
      sheetDragHandle = null,
      sheetPeekHeight = peekHeight.dp,
      sheetMaxWidth = 540.dp,
      sheetContent = {
        BottomSheets.Handle(modifier = Modifier.align(Alignment.CenterHorizontally))

        AdditionalActionsPopup(
          onDismissRequest = callScreenControlsListener::onDismissOverflow,
          state = additionalActionsState
        )

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = SHEET_TOP_PADDING.dp, bottom = SHEET_BOTTOM_PADDING.dp)
            .height(DimensionUnit.PIXELS.toDp(maxSheetHeight).dp)
            .onGloballyPositioned {
              val offset = it.positionInRoot().y
              val current = maxHeight - offset - DimensionUnit.DP.toPixels(peekHeight)
              val maximum = maxHeight - maxOffset - DimensionUnit.DP.toPixels(peekHeight)

              peekPercentage = current / maximum
            }
        ) {
          val callControlsAlpha = max(0f, 1 - peekPercentage)
          val callInfoAlpha = max(0f, peekPercentage)

          if (callInfoAlpha > 0f) {
            callInfoView(callInfoAlpha)
          }

          if (callControlsAlpha > 0f) {
            CallControls(
              callControlsState = callControlsState,
              callScreenControlsListener = callScreenControlsListener,
              callScreenSheetDisplayListener = callScreenSheetDisplayListener,
              displayVideoTooltip = callScreenState.displayVideoTooltip,
              additionalActionsState = additionalActionsState,
              audioOutputPickerController = callScreenController.audioOutputPickerController,
              modifier = Modifier
                .fillMaxWidth()
                .alpha(callControlsAlpha)
                .onSizeChanged {
                  peekHeight = DimensionUnit.PIXELS.toDp(it.height.toFloat()) + DRAG_HANDLE_HEIGHT + SHEET_TOP_PADDING + SHEET_BOTTOM_PADDING
                }
            )
          }
        }
      }
    ) {
      val padding by animateDpAsState(
        targetValue = if (scaffoldState.bottomSheetState.targetValue != SheetValue.Hidden) it.calculateBottomPadding() else 0.dp,
        label = "animate-as-state"
      )

      Viewport(
        localParticipant = localParticipant,
        localRenderState = localRenderState,
        webRtcCallState = webRtcCallState,
        callParticipantsPagerState = callParticipantsPagerState,
        overflowParticipants = overflowParticipants,
        scaffoldState = scaffoldState,
        callControlsState = callControlsState,
        callScreenState = callScreenState,
        onPipClick = onLocalPictureInPictureClicked,
        onPipFocusClick = onLocalPictureInPictureFocusClicked,
        onControlsToggled = onControlsToggled,
        callScreenController = callScreenController,
        onToggleCameraDirection = callScreenControlsListener::onCameraDirectionChanged,
        modifier = if (isPortrait) {
          Modifier.padding(bottom = padding)
        } else Modifier
      )

      val onCallInfoClick: () -> Unit = {
        scope.launch {
          if (scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
            scaffoldState.bottomSheetState.partialExpand()
          } else {
            scaffoldState.bottomSheetState.expand()
          }
        }
      }

      if (webRtcCallState.isPassedPreJoin) {
        AnimatedVisibility(
          visible = scaffoldState.bottomSheetState.targetValue != SheetValue.Hidden,
          enter = fadeIn(),
          exit = fadeOut()
        ) {
          CallScreenTopBar(
            callRecipient = callRecipient,
            callStatus = callScreenState.callStatus,
            onNavigationClick = onNavigationClick,
            onCallInfoClick = onCallInfoClick,
            modifier = Modifier.padding(bottom = padding)
          )
        }
      } else {
        CallScreenPreJoinOverlay(
          callRecipient = callRecipient,
          callStatus = callScreenState.callStatus,
          onNavigationClick = onNavigationClick,
          onCallInfoClick = onCallInfoClick,
          onCameraToggleClick = callScreenControlsListener::onCameraDirectionChanged,
          isLocalVideoEnabled = localParticipant.isVideoEnabled,
          isMoreThanOneCameraAvailable = localParticipant.isMoreThanOneCameraAvailable,
          modifier = Modifier.padding(bottom = padding)
        )
      }

      // This content lives "above" the controls sheet and includes raised hands, status updates, etc.
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(bottom = padding)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 20.dp)
        ) {
          CallScreenReactionsContainer(
            reactions = reactions,
            modifier = Modifier.weight(1f)
          )

          raiseHandSnackbar(
            Modifier
          )
        }

        AnimatedCallStateUpdate(
          callControlsChange = callScreenState.callControlsChange,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 20.dp)
        )

        val state = remember(callScreenState.pendingParticipantsState) {
          callScreenState.pendingParticipantsState
        }

        if (state != null) {
          PendingParticipants(
            pendingParticipantsState = state,
            pendingParticipantsListener = pendingParticipantsListener
          )
        }

        if (callScreenState.isParticipantUpdatePopupEnabled) {
          CallParticipantUpdatePopup(
            controller = callParticipantUpdatePopupController,
            modifier = Modifier.statusBarsPadding().fillMaxWidth()
          )
        }
      }
    }
  }

  CallScreenDialog(callScreenDialogType, onCallScreenDialogDismissed)
}

/**
 * Primary 'viewport' which will either render content above or behind the controls depending on
 * whether we are in landscape or portrait.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Viewport(
  localParticipant: CallParticipant,
  localRenderState: WebRtcLocalRenderState,
  webRtcCallState: WebRtcViewModel.State,
  callParticipantsPagerState: CallParticipantsPagerState,
  overflowParticipants: List<CallParticipant>,
  scaffoldState: BottomSheetScaffoldState,
  callControlsState: CallControlsState,
  callScreenState: CallScreenState,
  callScreenController: CallScreenController,
  onPipClick: () -> Unit,
  onPipFocusClick: () -> Unit,
  onControlsToggled: (Boolean) -> Unit,
  onToggleCameraDirection: () -> Unit,
  modifier: Modifier = Modifier
) {
  val isEmptyOngoingCall = webRtcCallState.inOngoingCall && callParticipantsPagerState.callParticipants.isEmpty()
  if (webRtcCallState.isPreJoinOrNetworkUnavailable || isEmptyOngoingCall) {
    LargeLocalVideoRenderer(
      localParticipant = localParticipant,
      modifier = modifier
    )

    return
  }

  val isLargeGroupCall = overflowParticipants.size > 1
  if (webRtcCallState.isPassedPreJoin) {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val scope = rememberCoroutineScope()

    val hideSheet by rememberUpdatedState(newValue = scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded && !callControlsState.skipHiddenState && !callScreenState.isDisplayingControlMenu())
    LaunchedEffect(callScreenController.restartTimerRequests, hideSheet) {
      if (hideSheet) {
        delay(5.seconds)
        scaffoldState.bottomSheetState.hide()
        onControlsToggled(false)
      }
    }

    BlurContainer(
      isBlurred = localRenderState == WebRtcLocalRenderState.FOCUSED,
      modifier = modifier.fillMaxWidth()
    ) {
      Row(modifier = Modifier.fillMaxSize()) {
        Column(
          modifier = Modifier.weight(1f)
        ) {
          CallParticipantsPager(
            callParticipantsPagerState = callParticipantsPagerState,
            pagerState = callScreenController.callParticipantsVerticalPagerState,
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f)
              .clickable(
                onClick = {
                  scope.launch {
                    callScreenController.handleEvent(CallScreenController.Event.TOGGLE_CONTROLS)
                  }
                },
                enabled = !callControlsState.skipHiddenState
              )
          )

          if (isPortrait && isLargeGroupCall) {
            Row {
              CallParticipantsOverflow(
                lineType = LayoutStrategyLineType.ROW,
                overflowParticipants = overflowParticipants,
                modifier = Modifier
                  .padding(vertical = 16.dp)
                  .height(CallScreenMetrics.SmallRendererSize)
                  .weight(1f)
              )
            }
          }
        }

        if (!isPortrait && isLargeGroupCall) {
          Column {
            CallParticipantsOverflow(
              lineType = LayoutStrategyLineType.COLUMN,
              overflowParticipants = overflowParticipants,
              modifier = Modifier
                .padding(horizontal = 16.dp)
                .width(CallScreenMetrics.SmallRendererSize)
                .weight(1f)
            )
          }
        }
      }
    }
  }

  if (webRtcCallState.inOngoingCall && localParticipant.isVideoEnabled) {
    MoveableLocalVideoRenderer(
      localParticipant = localParticipant,
      localRenderState = localRenderState,
      onClick = onPipClick,
      onToggleCameraDirectionClick = onToggleCameraDirection,
      onFocusLocalParticipantClick = onPipFocusClick,
      modifier = modifier
    )
  }
}

/**
 * Full-screen local video renderer displayed when the user is in pre-call state.
 */
@Composable
private fun LargeLocalVideoRenderer(
  localParticipant: CallParticipant,
  modifier: Modifier = Modifier
) {
  CallParticipantRenderer(
    callParticipant = localParticipant,
    isLocalParticipant = true,
    renderInPip = false,
    modifier = modifier
      .fillMaxSize()
  )
}

/**
 * Wrapper for a CallStateUpdate popup that animates its display on the screen, sliding up from either
 * above the controls or from the bottom of the screen if the controls are hidden.
 */
@Composable
private fun AnimatedCallStateUpdate(
  callControlsChange: CallControlsChange?,
  modifier: Modifier = Modifier
) {
  AnimatedContent(
    label = "call-state-update",
    targetState = callControlsChange,
    contentAlignment = Alignment.BottomCenter,
    transitionSpec = {
      (
        fadeIn(animationSpec = tween(220, delayMillis = 90)) +
          scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90))
        )
        .togetherWith(fadeOut(animationSpec = tween(90)))
        .using(sizeTransform = null)
    },
    modifier = modifier
  ) {
    if (it != null) {
      CallStateUpdatePopup(
        callControlsChange = it
      )
    }
  }
}

@AllNightPreviews
@Composable
private fun CallScreenPreview() {
  val participants = remember {
    (1..10).map {
      CallParticipant(
        callParticipantId = CallParticipantId(0, RecipientId.from(it.toLong())),
        recipient = Recipient(
          isResolving = false,
          chatColorsValue = ChatColorsPalette.UNKNOWN_CONTACT
        )
      )
    }
  }

  Previews.Preview {
    CallScreen(
      callRecipient = Recipient(systemContactName = "Test User"),
      webRtcCallState = WebRtcViewModel.State.CALL_CONNECTED,
      isRemoteVideoOffer = false,
      isInPipMode = false,
      callScreenState = CallScreenState(
        callStatus = "Connecting..."
      ),
      callControlsState = CallControlsState(
        displayMicToggle = true,
        isMicEnabled = true,
        displayVideoToggle = true,
        displayGroupRingingToggle = true,
        displayStartCallButton = true
      ),
      callParticipantsPagerState = CallParticipantsPagerState(
        callParticipants = participants,
        focusedParticipant = participants.first()
      ),
      localParticipant = CallParticipant(
        recipient = Recipient(
          isResolving = false,
          isSelf = true
        ),
        isVideoEnabled = true,
        cameraState = CameraState(
          CameraState.Direction.FRONT,
          2
        )
      ),
      localRenderState = WebRtcLocalRenderState.SMALLER_RECTANGLE,
      callScreenDialogType = CallScreenDialogType.NONE,
      callInfoView = {
        Text(text = "Call Info View Preview", modifier = Modifier.alpha(it))
      },
      raiseHandSnackbar = {
        RaiseHandSnackbar.View(
          raisedHandsState = listOf(
            GroupCallRaiseHandEvent(
              sender = CallParticipant(
                recipient = Recipient(
                  isResolving = false,
                  systemContactName = "Miles Morales"
                )
              ),
              timestampMillis = System.currentTimeMillis()
            )
          ),
          speechEvent = null,
          showCallInfoListener = {},
          modifier = it
        )
      },
      onNavigationClick = {},
      onLocalPictureInPictureClicked = {},
      onLocalPictureInPictureFocusClicked = {},
      overflowParticipants = participants,
      onControlsToggled = {},
      reactions = emptyList(),
      callParticipantUpdatePopupController = remember { CallParticipantUpdatePopupController() }
    )
  }
}
