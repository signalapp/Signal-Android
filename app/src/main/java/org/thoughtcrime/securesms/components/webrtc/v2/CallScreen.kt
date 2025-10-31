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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.TriggerAlignedPopupState
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.WebRtcLocalRenderState
import org.thoughtcrime.securesms.conversation.colors.ChatColorsPalette
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.events.GroupCallReactionEvent
import org.thoughtcrime.securesms.events.WebRtcViewModel
import org.thoughtcrime.securesms.recipients.Recipient
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
  overflowParticipants: List<CallParticipant>,
  localParticipant: CallParticipant,
  localRenderState: WebRtcLocalRenderState,
  callScreenDialogType: CallScreenDialogType,
  reactions: List<GroupCallReactionEvent>,
  callInfoView: @Composable (Float) -> Unit,
  raiseHandSnackbar: @Composable (Modifier) -> Unit,
  onNavigationClick: () -> Unit,
  onLocalPictureInPictureClicked: () -> Unit,
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

    var offset by remember { mutableFloatStateOf(0f) }
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
              offset = scaffoldState.bottomSheetState.requireOffset()
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
        onControlsToggled = onControlsToggled,
        callScreenController = callScreenController,
        modifier = if (isPortrait) {
          Modifier.padding(bottom = padding)
        } else Modifier
      )

      CallScreenReactionsContainer(
        reactions = reactions,
        modifier = Modifier.padding(bottom = padding)
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
          isLocalVideoEnabled = localParticipant.isVideoEnabled,
          modifier = Modifier.padding(bottom = padding)
        )
      }

      raiseHandSnackbar(Modifier.fillMaxWidth())

      AnimatedCallStateUpdate(
        callControlsChange = callScreenState.callControlsChange,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = padding)
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
private fun BoxScope.Viewport(
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
  onControlsToggled: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  if (webRtcCallState.isPreJoinOrNetworkUnavailable) {
    LargeLocalVideoRenderer(
      localParticipant = localParticipant,
      modifier = modifier
    )
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

    var spacerOffset by remember { mutableStateOf(Offset.Zero) }

    Row(modifier = modifier.fillMaxWidth()) {
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
          val overflowSize = dimensionResource(R.dimen.call_screen_overflow_item_size)
          val selfPipSize = rememberTinyPortraitSize()

          Row {
            CallParticipantsOverflow(
              overflowParticipants = overflowParticipants,
              modifier = Modifier
                .padding(top = 16.dp, start = 16.dp, bottom = 16.dp)
                .height(overflowSize)
                .weight(1f)
            )

            Spacer(
              modifier = Modifier
                .onPlaced { coordinates ->
                  spacerOffset = coordinates.localToRoot(Offset.Zero)
                }
                .padding(top = 16.dp, bottom = 16.dp, end = 16.dp)
                .size(selfPipSize.small)
            )
          }
        }
      }

      if (!isPortrait && isLargeGroupCall) {
        val overflowSize = dimensionResource(R.dimen.call_screen_overflow_item_size)
        val selfPipSize = rememberTinyPortraitSize()

        Column {
          CallParticipantsOverflow(
            overflowParticipants = overflowParticipants,
            modifier = Modifier
              .width(overflowSize + 32.dp)
              .weight(1f)
          )

          Spacer(
            modifier = Modifier
              .onPlaced { coordinates ->
                spacerOffset = coordinates.localToRoot(Offset.Zero)
              }
              .size(selfPipSize.small)
          )
        }
      }
    }

    if (isLargeGroupCall) {
      TinyLocalVideoRenderer(
        localParticipant = localParticipant,
        localRenderState = localRenderState,
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(
            start = with(LocalDensity.current) {
              spacerOffset.x.toDp()
            },
            top = with(LocalDensity.current) {
              spacerOffset.y.toDp()
            }
          ),
        onClick = onPipClick
      )
    }
  }

  if (webRtcCallState.inOngoingCall && localParticipant.isVideoEnabled && !isLargeGroupCall) {
    SmallMoveableLocalVideoRenderer(
      localParticipant = localParticipant,
      localRenderState = localRenderState,
      onClick = onPipClick,
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
    modifier = modifier
      .fillMaxSize()
  )
}

/**
 * Tiny expandable video renderer displayed when the user is in a large group call.
 */
@Composable
private fun TinyLocalVideoRenderer(
  localParticipant: CallParticipant,
  localRenderState: WebRtcLocalRenderState,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  val (smallSize, expandedSize, padding) = rememberTinyPortraitSize()
  val size = if (localRenderState == WebRtcLocalRenderState.EXPANDED) expandedSize else smallSize

  val width by animateDpAsState(label = "tiny-width", targetValue = size.width)
  val height by animateDpAsState(label = "tiny-height", targetValue = size.height)

  if (LocalInspectionMode.current) {
    Text(
      "Test ${currentWindowAdaptiveInfo().windowSizeClass}",
      modifier = modifier
        .padding(padding)
        .height(height)
        .width(width)
        .background(color = Color.Red)
        .clip(RoundedCornerShape(8.dp))
        .clickable(onClick = onClick)
    )
  }

  CallParticipantRenderer(
    callParticipant = localParticipant,
    modifier = modifier
      .padding(padding)
      .height(height)
      .width(width)
      .clip(RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
  )
}

/**
 * Small moveable local video renderer that displays the user's video in a draggable and expandable view.
 */
@Composable
private fun SmallMoveableLocalVideoRenderer(
  localParticipant: CallParticipant,
  localRenderState: WebRtcLocalRenderState,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

  val smallSize = remember(isPortrait) {
    if (isPortrait) DpSize(90.dp, 160.dp) else DpSize(160.dp, 90.dp)
  }
  val expandedSize = remember(isPortrait) {
    if (isPortrait) DpSize(180.dp, 320.dp) else DpSize(320.dp, 180.dp)
  }

  val size = if (localRenderState == WebRtcLocalRenderState.EXPANDED) expandedSize else smallSize

  val targetWidth by animateDpAsState(label = "animate-pip-width", targetValue = size.width, animationSpec = tween())
  val targetHeight by animateDpAsState(label = "animate-pip-height", targetValue = size.height, animationSpec = tween())

  PictureInPicture(
    contentSize = DpSize(targetWidth, targetHeight),
    modifier = Modifier
      .fillMaxSize()
      .then(modifier)
      .padding(16.dp)
      .statusBarsPadding()
  ) {
    CallParticipantRenderer(
      callParticipant = localParticipant,
      modifier = Modifier
        .fillMaxSize()
        .clip(MaterialTheme.shapes.medium)
        .clickable(onClick = {
          onClick()
        })
    )
  }
}

@Composable
private fun rememberTinyPortraitSize(): SelfPictureInPictureDimensions {
  val smallWidth = dimensionResource(R.dimen.call_screen_overflow_item_size)
  val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
  val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

  val smallSize = when {
    windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT && !isLandscape -> DpSize(40.dp, smallWidth)
    windowSizeClass.windowHeightSizeClass == WindowHeightSizeClass.COMPACT && isLandscape -> DpSize(smallWidth, 40.dp)
    windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED -> DpSize(124.dp, 217.dp)
    else -> DpSize(smallWidth, smallWidth)
  }

  val expandedSize = when {
    windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT && !isLandscape -> DpSize(180.dp, 320.dp)
    windowSizeClass.windowHeightSizeClass == WindowHeightSizeClass.COMPACT && isLandscape -> DpSize(320.dp, 180.dp)
    else -> DpSize(smallWidth, smallWidth)
  }

  val padding = when {
    windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT && !isLandscape -> PaddingValues(vertical = 16.dp)
    else -> PaddingValues(16.dp)
  }

  return remember(windowSizeClass) {
    SelfPictureInPictureDimensions(smallSize, expandedSize, padding)
  }
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

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.FOLDABLE)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.TABLET)
@Composable
private fun CallScreenPreview() {
  val participants = remember {
    (1..10).map {
      CallParticipant(
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
        isVideoEnabled = true
      ),
      localRenderState = WebRtcLocalRenderState.SMALL_RECTANGLE,
      callScreenDialogType = CallScreenDialogType.NONE,
      callInfoView = {
        Text(text = "Call Info View Preview", modifier = Modifier.alpha(it))
      },
      raiseHandSnackbar = {},
      onNavigationClick = {},
      onLocalPictureInPictureClicked = {},
      overflowParticipants = participants,
      onControlsToggled = {},
      reactions = emptyList()
    )
  }
}

data class SelfPictureInPictureDimensions(
  val small: DpSize,
  val expanded: DpSize,
  val paddingValues: PaddingValues
)
