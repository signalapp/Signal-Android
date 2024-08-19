/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
  localParticipant: CallParticipant,
  localRenderState: WebRtcLocalRenderState,
  callInfoView: @Composable (Float) -> Unit,
  onNavigationClick: () -> Unit,
  onLocalPictureInPictureClicked: () -> Unit
) {
  var peekPercentage by remember {
    mutableFloatStateOf(0f)
  }

  val scope = rememberCoroutineScope()
  val scaffoldState = rememberBottomSheetScaffoldState(
    bottomSheetState = rememberStandardBottomSheetState(
      confirmValueChange = {
        !(it == SheetValue.Hidden && callControlsState.skipHiddenState)
      },
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
          scaffoldState = scaffoldState,
          callControlsState = callControlsState,
          onPipClick = onLocalPictureInPictureClicked
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
            scaffoldState = scaffoldState,
            callControlsState = callControlsState,
            onPipClick = onLocalPictureInPictureClicked
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
        CallScreenTopBar(
          callRecipient = callRecipient,
          callStatus = callScreenState.callStatus,
          onNavigationClick = onNavigationClick,
          onCallInfoClick = onCallInfoClick,
          modifier = Modifier.padding(bottom = padding)
        )
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

      AnimatedCallStateUpdate(
        callControlsChange = callScreenState.callControlsChange,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = padding)
          .padding(bottom = 20.dp)
      )
    }
  }
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
  scaffoldState: BottomSheetScaffoldState,
  callControlsState: CallControlsState,
  onPipClick: () -> Unit
) {
  LargeLocalVideoRenderer(
    localParticipant = localParticipant,
    localRenderState = localRenderState
  )

  if (webRtcCallState.isPassedPreJoin) {
    val scope = rememberCoroutineScope()

    CallParticipantsPager(
      callParticipantsPagerState = callParticipantsPagerState,
      modifier = Modifier
        .fillMaxSize()
        .clip(MaterialTheme.shapes.extraLarge)
        .clickable(
          onClick = {
            scope.launch {
              if (scaffoldState.bottomSheetState.isVisible) {
                scaffoldState.bottomSheetState.hide()
              } else {
                scaffoldState.bottomSheetState.show()
              }
            }
          },
          enabled = !callControlsState.skipHiddenState
        )
    )
  }

  if (webRtcCallState.inOngoingCall && localParticipant.isVideoEnabled) {
    val padBottom: Dp = if (scaffoldState.bottomSheetState.isVisible) {
      0.dp
    } else {
      val density = LocalDensity.current
      with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    }

    SmallMoveableLocalVideoRenderer(
      localParticipant = localParticipant,
      localRenderState = localRenderState,
      extraPadBottom = padBottom,
      onClick = onPipClick
    )
  }
}

@Composable
private fun LargeLocalVideoRenderer(
  localParticipant: CallParticipant,
  localRenderState: WebRtcLocalRenderState
) {
  CallParticipantVideoRenderer(
    callParticipant = localParticipant,
    attachVideoSink = localRenderState == WebRtcLocalRenderState.LARGE,
    modifier = Modifier
      .fillMaxSize()
      .clip(MaterialTheme.shapes.extraLarge)
  )
}

@Composable
private fun SmallMoveableLocalVideoRenderer(
  localParticipant: CallParticipant,
  localRenderState: WebRtcLocalRenderState,
  extraPadBottom: Dp,
  onClick: () -> Unit
) {
  val smallSize = DpSize(90.dp, 160.dp)
  val largeSize = DpSize(180.dp, 320.dp)

  val size = if (localRenderState == WebRtcLocalRenderState.SMALL_RECTANGLE) smallSize else largeSize

  val targetWidth by animateDpAsState(label = "animate-pip-width", targetValue = size.width, animationSpec = tween())
  val targetHeight by animateDpAsState(label = "animate-pip-height", targetValue = size.height, animationSpec = tween())
  val bottomPadding by animateDpAsState(label = "animate-pip-bottom-pad", targetValue = extraPadBottom, animationSpec = tween())

  PictureInPicture(
    contentSize = DpSize(targetWidth, targetHeight),
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
      .statusBarsPadding()
      .padding(bottom = bottomPadding)
  ) {
    CallParticipantVideoRenderer(
      callParticipant = localParticipant,
      attachVideoSink = localRenderState == WebRtcLocalRenderState.SMALL_RECTANGLE || localRenderState == WebRtcLocalRenderState.EXPANDED,
      modifier = Modifier
        .fillMaxSize()
        .clip(MaterialTheme.shapes.medium)
        .clickable {
          onClick()
        }
    )
  }
}

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
      callRecipient = Recipient.UNKNOWN,
      webRtcCallState = WebRtcViewModel.State.CALL_CONNECTED,
      callScreenState = CallScreenState(),
      callControlsState = CallControlsState(
        displayMicToggle = true,
        isMicEnabled = true,
        displayVideoToggle = true,
        displayGroupRingingToggle = true,
        displayStartCallButton = true
      ),
      callInfoView = {
        Text(text = "Call Info View Preview", modifier = Modifier.alpha(it))
      },
      localParticipant = CallParticipant(),
      localRenderState = WebRtcLocalRenderState.LARGE,
      callParticipantsPagerState = CallParticipantsPagerState(),
      onNavigationClick = {},
      onLocalPictureInPictureClicked = {}
    )
  }
}
