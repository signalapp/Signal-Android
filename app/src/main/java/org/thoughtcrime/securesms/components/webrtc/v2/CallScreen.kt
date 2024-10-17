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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Previews
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.components.webrtc.WebRtcLocalRenderState
import org.thoughtcrime.securesms.events.CallParticipant
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
  callScreenState: CallScreenState,
  callControlsState: CallControlsState,
  callControlsCallback: CallControlsCallback = CallControlsCallback.Empty,
  callParticipantsPagerState: CallParticipantsPagerState,
  overflowParticipants: List<CallParticipant>,
  localParticipant: CallParticipant,
  localRenderState: WebRtcLocalRenderState,
  callScreenDialogType: CallScreenDialogType,
  callInfoView: @Composable (Float) -> Unit,
  raiseHandSnackbar: @Composable (Modifier) -> Unit,
  onNavigationClick: () -> Unit,
  onLocalPictureInPictureClicked: () -> Unit,
  onControlsToggled: (Boolean) -> Unit,
  onCallScreenDialogDismissed: () -> Unit = {}
) {
  var peekPercentage by remember {
    mutableFloatStateOf(0f)
  }

  val skipHiddenState by rememberUpdatedState(newValue = callControlsState.skipHiddenState)
  val valueChangeOperation: (SheetValue) -> Boolean = remember {
    {
      !(it == SheetValue.Hidden && skipHiddenState)
    }
  }

  val scope = rememberCoroutineScope()
  val scaffoldState = rememberBottomSheetScaffoldState(
    bottomSheetState = rememberStandardBottomSheetState(
      confirmValueChange = valueChangeOperation,
      skipHiddenState = false
    )
  )
  val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

  BoxWithConstraints {
    val maxHeight = constraints.maxHeight
    val maxSheetHeight = round(constraints.maxHeight * 0.66f)
    val maxOffset = maxHeight - maxSheetHeight

    var offset by remember { mutableFloatStateOf(0f) }
    var peekHeight by remember { mutableFloatStateOf(88f) }

    BottomSheetScaffold(
      scaffoldState = scaffoldState,
      sheetDragHandle = null,
      sheetPeekHeight = peekHeight.dp,
      sheetMaxWidth = 540.dp,
      sheetContent = {
        BottomSheets.Handle(modifier = Modifier.align(Alignment.CenterHorizontally))

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
              callControlsCallback = callControlsCallback,
              displayVideoTooltip = callScreenState.displayVideoTooltip,
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

      if (!isPortrait) {
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
          onControlsToggled = onControlsToggled
        )
      }

      Box(
        modifier = Modifier
          .padding(bottom = padding)
          .fillMaxSize()
      ) {
        if (isPortrait) {
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
            onControlsToggled = onControlsToggled
          )
        }
      }

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
  onPipClick: () -> Unit,
  onControlsToggled: (Boolean) -> Unit
) {
  if (webRtcCallState.isPreJoinOrNetworkUnavailable) {
    LargeLocalVideoRenderer(
      localParticipant = localParticipant,
      localRenderState = localRenderState
    )
  }

  val isLargeGroupCall = overflowParticipants.size > 1
  if (webRtcCallState.isPassedPreJoin) {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val scope = rememberCoroutineScope()

    val hideSheet by rememberUpdatedState(newValue = scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded && !callControlsState.skipHiddenState && !callScreenState.isDisplayingAudioToggleSheet)
    LaunchedEffect(hideSheet) {
      if (hideSheet) {
        delay(5.seconds)
        scaffoldState.bottomSheetState.hide()
        onControlsToggled(false)
      }
    }

    Row {
      Column(
        modifier = Modifier.weight(1f)
      ) {
        CallParticipantsPager(
          callParticipantsPagerState = callParticipantsPagerState,
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(
              onClick = {
                scope.launch {
                  if (scaffoldState.bottomSheetState.isVisible) {
                    scaffoldState.bottomSheetState.hide()
                    onControlsToggled(false)
                  } else {
                    onControlsToggled(true)
                    scaffoldState.bottomSheetState.show()
                  }
                }
              },
              enabled = !callControlsState.skipHiddenState
            )
        )

        if (isPortrait && isLargeGroupCall) {
          CallParticipantsOverflow(
            overflowParticipants = overflowParticipants,
            modifier = Modifier
              .padding(16.dp)
              .height(72.dp)
              .fillMaxWidth()
          )
        }
      }

      if (!isPortrait && isLargeGroupCall) {
        CallParticipantsOverflow(
          overflowParticipants = overflowParticipants,
          modifier = Modifier
            .padding(16.dp)
            .width(72.dp)
            .fillMaxHeight()
        )
      }
    }

    if (isLargeGroupCall) {
      TinyLocalVideoRenderer(
        localParticipant = localParticipant,
        localRenderState = localRenderState,
        modifier = Modifier.align(Alignment.BottomEnd),
        onClick = onPipClick
      )
    }
  }

  if (webRtcCallState.inOngoingCall && localParticipant.isVideoEnabled && !isLargeGroupCall) {
    SmallMoveableLocalVideoRenderer(
      localParticipant = localParticipant,
      localRenderState = localRenderState,
      onClick = onPipClick
    )
  }
}

/**
 * Full-screen local video renderer displayed when the user is in pre-call state.
 */
@Composable
private fun LargeLocalVideoRenderer(
  localParticipant: CallParticipant,
  localRenderState: WebRtcLocalRenderState
) {
  LocalParticipantRenderer(
    localParticipant = localParticipant,
    localRenderState = localRenderState,
    modifier = Modifier
      .fillMaxSize()
      .clip(MaterialTheme.shapes.extraLarge)
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
  val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

  val smallSize = remember(isPortrait) {
    if (isPortrait) DpSize(40.dp, 72.dp) else DpSize(72.dp, 40.dp)
  }
  val expandedSize = remember(isPortrait) {
    if (isPortrait) DpSize(180.dp, 320.dp) else DpSize(320.dp, 180.dp)
  }

  val size = if (localRenderState == WebRtcLocalRenderState.EXPANDED) expandedSize else smallSize

  val width by animateDpAsState(label = "tiny-width", targetValue = size.width)
  val height by animateDpAsState(label = "tiny-height", targetValue = size.height)

  LocalParticipantRenderer(
    localParticipant = localParticipant,
    localRenderState = localRenderState,
    modifier = modifier
      .padding(16.dp)
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
  onClick: () -> Unit
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
      .padding(16.dp)
      .statusBarsPadding()
  ) {
    LocalParticipantRenderer(
      localParticipant = localParticipant,
      localRenderState = localRenderState,
      modifier = Modifier
        .fillMaxSize()
        .clip(MaterialTheme.shapes.medium)
        .clickable(onClick = {
          onClick()
        })
    )
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
@Composable
private fun CallScreenPreview() {
  Previews.Preview {
    CallScreen(
      callRecipient = Recipient(systemContactName = "Test User"),
      webRtcCallState = WebRtcViewModel.State.CALL_CONNECTED,
      callScreenState = CallScreenState(),
      callControlsState = CallControlsState(
        displayMicToggle = true,
        isMicEnabled = true,
        displayVideoToggle = true,
        displayGroupRingingToggle = true,
        displayStartCallButton = true
      ),
      callParticipantsPagerState = CallParticipantsPagerState(),
      localParticipant = CallParticipant(),
      localRenderState = WebRtcLocalRenderState.LARGE,
      callScreenDialogType = CallScreenDialogType.NONE,
      callInfoView = {
        Text(text = "Call Info View Preview", modifier = Modifier.alpha(it))
      },
      raiseHandSnackbar = {},
      onNavigationClick = {},
      onLocalPictureInPictureClicked = {},
      overflowParticipants = emptyList(),
      onControlsToggled = {}
    )
  }
}
