/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.linkaccount

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.signal.core.ui.FormFactor
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.assumedFormFactor
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.LocalAnimateVisibilityScope
import org.signal.core.ui.compose.LocalSharedTransitionScope
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.QrCode
import org.signal.core.ui.compose.QrCodeData
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.rememberWindowBreakpoint
import org.signal.registration.R
import org.signal.registration.screens.OnePaneRegistrationScaffold
import org.signal.registration.screens.RegistrationScaffold
import org.signal.registration.screens.TwoPaneRegistrationScaffold
import org.signal.registration.screens.attachDebugLogHelper
import org.signal.registration.screens.quickrestore.QrState
import org.signal.registration.test.TestTags

private val OVERLAY_HORIZONTAL_PADDING = 24.dp
private const val EXPAND_BUTTON_FADE_DURATION_MS = 100
private const val QR_MORPH_DURATION_MS = 300

/**
 * Screen which will display a QR code for linking this device as a secondary.
 */
@Composable
fun LinkAccountScreen(
  state: LinkAccountScreenState,
  onEvent: (LinkAccountScreenEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  val layoutParams = RegistrationScaffold.rememberLayoutParams()
  val isPhone = rememberWindowBreakpoint().assumedFormFactor == FormFactor.PHONE

  // Sequence the expand button animation with the QR morph
  var expandButtonVisible by remember { mutableStateOf(!state.displayQrOverlay) }
  LaunchedEffect(state.displayQrOverlay) {
    if (state.displayQrOverlay) {
      expandButtonVisible = false
    } else {
      delay(QR_MORPH_DURATION_MS.toLong())
      expandButtonVisible = true
    }
  }

  Surface(modifier = modifier.testTag(TestTags.LINK_ACCOUNT_SCREEN)) {
    SharedTransitionLayout {
      AnimatedContent(
        targetState = state.displayQrOverlay,
        label = "qr_code_fullscreen_transition",
        transitionSpec = {
          fadeIn(animationSpec = tween(durationMillis = QR_MORPH_DURATION_MS, delayMillis = if (targetState) EXPAND_BUTTON_FADE_DURATION_MS else 0)) togetherWith fadeOut(tween(EXPAND_BUTTON_FADE_DURATION_MS))
        }
      ) { target ->
        CompositionLocalProvider(
          LocalSharedTransitionScope provides this@SharedTransitionLayout,
          LocalAnimateVisibilityScope provides this
        ) {
          if (target) {
            QrCodeOverlay(state, onEvent, isPhone)
          } else {
            when (layoutParams) {
              is RegistrationScaffold.Params.OnePane -> OnePane(layoutParams, isPhone, expandButtonVisible, state, onEvent)
              is RegistrationScaffold.Params.TwoPane -> TwoPane(layoutParams, expandButtonVisible, state, onEvent)
            }
          }
        }
      }
    }

    StateDialogs(state = state, onEvent = onEvent)
  }
}

@Composable
private fun StateDialogs(
  state: LinkAccountScreenState,
  onEvent: (LinkAccountScreenEvent) -> Unit
) {
  if (state.isRegistering) {
    Dialogs.IndeterminateProgressDialog(
      message = stringResource(R.string.LinkAccountScreen__linking_device)
    )
  } else if (state.isWaitingForPrimary) {
    Dialogs.IndeterminateProgressDialog(
      message = stringResource(R.string.LinkAccountScreen__waiting_for_your_other_device)
    )
  }

  if (state.showError) {
    Dialogs.SimpleMessageDialog(
      message = stringResource(R.string.LinkAccountScreen__error_linking_device),
      dismiss = stringResource(android.R.string.ok),
      onDismiss = { onEvent(LinkAccountScreenEvent.DismissError) }
    )
  }

  if (state.showDeleteDataDialog) {
    Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.LinkAccountScreen__delete_app_data_question),
      body = stringResource(R.string.LinkAccountScreen__you_are_attempting_to_link_a_different_account),
      confirm = stringResource(R.string.LinkAccountScreen__delete_and_restart),
      confirmColor = MaterialTheme.colorScheme.error,
      dismiss = stringResource(android.R.string.cancel),
      onConfirm = { onEvent(LinkAccountScreenEvent.ConfirmDeleteAndRelink) },
      onDeny = { onEvent(LinkAccountScreenEvent.CancelDeleteAndRelink) },
      properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )
  }
}

@Composable
private fun OnePane(
  params: RegistrationScaffold.Params.OnePane,
  isPhone: Boolean,
  expandButtonVisible: Boolean,
  state: LinkAccountScreenState,
  onEvent: (LinkAccountScreenEvent) -> Unit
) {
  val scrollState = rememberScrollState()

  OnePaneRegistrationScaffold(
    params = params,
    content = { paddingValues ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = spacedBy(if (isPhone) 32.dp else 64.dp),
        modifier = Modifier
          .verticalScroll(scrollState)
          .padding(paddingValues)
      ) {
        Title()

        QrCodeContent(state = state, onEvent = onEvent, isPhone = isPhone, expandButtonVisible = expandButtonVisible)

        Steps(
          verticalArrangement = spacedBy(if (isPhone) 20.dp else 32.dp),
          centerGetHelp = isPhone,
          onEvent = onEvent
        )
      }
    },
    footer = if (state.showCreateAccount) {
      {
        OnePaneFooterContent(
          params = params,
          isElevated = scrollState.canScrollForward,
          onEvent = onEvent
        )
      }
    } else {
      null
    }
  )
}

@Composable
private fun TwoPane(
  params: RegistrationScaffold.Params.TwoPane,
  expandButtonVisible: Boolean,
  state: LinkAccountScreenState,
  onEvent: (LinkAccountScreenEvent) -> Unit
) {
  TwoPaneRegistrationScaffold(
    params = params,
    firstPane = { paddingValues ->
      FirstPaneContent(
        onEvent = onEvent,
        modifier = Modifier
          .padding(paddingValues)
          .weight(1f)
      )
    },
    secondPane = { paddingValues ->
      QrCodeContent(
        state = state,
        onEvent = onEvent,
        modifier = Modifier
          .weight(1f)
          .padding(paddingValues),
        expandButtonVisible = expandButtonVisible
      )
    },
    footer = if (state.showCreateAccount) {
      {
        TwoPaneFooterContent(
          params = params,
          isElevated = false,
          onEvent = onEvent
        )
      }
    } else {
      null
    }
  )
}

@Composable
private fun FirstPaneContent(
  onEvent: (LinkAccountScreenEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier,
    verticalArrangement = spacedBy(32.dp)
  ) {
    Title()

    Steps(verticalArrangement = spacedBy(32.dp), centerGetHelp = false, onEvent = onEvent)
  }
}

@Composable
private fun Title() {
  Text(
    text = stringResource(R.string.LinkAccountScreen__scan_this_code_to_link_your_account),
    style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier
      .fillMaxWidth()
      .attachDebugLogHelper()
  )
}

@Composable
private fun Steps(
  verticalArrangement: Arrangement.Vertical,
  centerGetHelp: Boolean,
  onEvent: (LinkAccountScreenEvent) -> Unit
) {
  Column(verticalArrangement = verticalArrangement) {
    Step(
      icon = SignalIcons.DevicePhone.imageVector,
      text = stringResource(R.string.LinkAccountScreen__open_signal_on_your_phone)
    )

    Step(
      icon = SignalIcons.PersonCircle.imageVector,
      text = stringResource(R.string.LinkAccountScreen__tap_your_profile_picture_to_open_signal_settings)
    )

    Step(
      icon = SignalIcons.Devices.imageVector,
      text = stringResource(R.string.LinkAccountScreen__tap_linked_devices_and_link_new_device)
    )

    GetHelp(
      onEvent = onEvent,
      modifier = if (centerGetHelp) Modifier.align(Alignment.CenterHorizontally) else Modifier
    )
  }
}

@Composable
private fun GetHelp(
  onEvent: (LinkAccountScreenEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  TextButton(
    onClick = { onEvent(LinkAccountScreenEvent.GetHelpClick) },
    modifier = modifier.testTag(TestTags.LINK_ACCOUNT_GET_HELP_BUTTON)
  ) {
    Text(text = stringResource(R.string.LinkAccountScreen__get_help_with_these_steps))
  }
}

@Composable
private fun Step(icon: ImageVector, text: String) {
  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(
      horizontalArrangement = spacedBy(24.dp)
    ) {
      Icon(imageVector = icon, contentDescription = null)
      Text(text = text)
    }
  }
}

@Composable
private fun QrCodeContent(
  state: LinkAccountScreenState,
  onEvent: (LinkAccountScreenEvent) -> Unit,
  modifier: Modifier = Modifier,
  isPhone: Boolean = false,
  isInOverlay: Boolean = false,
  expandButtonVisible: Boolean = true,
  overlayMaxWidth: Dp? = null
) {
  val sharedTransitionScope = LocalSharedTransitionScope.current!!
  val animatedVisibilityScope = LocalAnimateVisibilityScope.current!!

  // Delay the morph on expand (so the button can fade out first) but not on collapse
  val expanding = state.displayQrOverlay
  val qrBoundsTransform = remember(expanding) {
    BoundsTransform { _, _ ->
      tween(durationMillis = QR_MORPH_DURATION_MS, delayMillis = if (expanding) EXPAND_BUTTON_FADE_DURATION_MS else 0)
    }
  }

  Box(
    contentAlignment = if (isInOverlay) Alignment.Center else Alignment.CenterEnd,
    modifier = modifier
  ) {
    with(sharedTransitionScope) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .sharedElement(
            sharedContentState = rememberSharedContentState("qr_code_outer_border"),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = qrBoundsTransform
          )
          .size(getQrOuterBorderSize(isInOverlay, overlayMaxWidth))
          .background(color = colorResource(org.signal.core.ui.R.color.signal_light_colorPrimary), shape = RoundedCornerShape(if (isPhone) 48.dp else 64.dp))
      ) {
        AnimatedContent(
          targetState = state.qrCodeState,
          modifier = Modifier
            .sharedElement(
              sharedContentState = rememberSharedContentState("qr_code_inner_border"),
              animatedVisibilityScope = animatedVisibilityScope,
              boundsTransform = qrBoundsTransform
            )
            .size(getQrInnerBorderSize(isInOverlay, overlayMaxWidth))
            .background(color = Color.White, shape = RoundedCornerShape(if (isPhone) 26.dp else 24.dp))
        ) { target ->
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
          ) {
            when (target) {
              QrState.Failed -> QrCodeFailed(onEvent)
              is QrState.Loaded -> QrCodeDisplay(target.qrCodeData, isInOverlay, overlayMaxWidth, qrBoundsTransform, sharedTransitionScope, animatedVisibilityScope)
              QrState.Loading -> QrCodeLoading()
              QrState.Scanned -> QrCodeScanned()
            }
          }
        }
      }
    }

    AnimatedVisibility(
      visible = state.qrCodeState is QrState.Loaded && !isInOverlay && expandButtonVisible,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .then(if (isPhone) Modifier.offset(x = 6.dp, y = (-6).dp) else Modifier)
        .then(with(sharedTransitionScope) { Modifier.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f) }),
      enter = fadeIn(tween(EXPAND_BUTTON_FADE_DURATION_MS)),
      exit = fadeOut(tween(EXPAND_BUTTON_FADE_DURATION_MS))
    ) {
      IconButtons.IconButton(
        onClick = { onEvent(LinkAccountScreenEvent.DisplayOverlayClick) },
        size = if (isPhone) 40.dp else 53.dp,
        colors = IconButtons.iconButtonColors(
          containerColor = Color(0xFF506DCD),
          contentColor = colorResource(org.signal.core.ui.R.color.signal_light_colorOnPrimary)
        ),
        modifier = Modifier.testTag(TestTags.LINK_ACCOUNT_DISPLAY_OVERLAY_BUTTON)
      ) {
        Icon(
          imageVector = SignalIcons.Maximize.imageVector,
          contentDescription = stringResource(R.string.LinkAccountScreen__maximize_qr_code)
        )
      }
    }
  }
}

@Composable
private fun QrCodeDisplay(
  qrCodeData: QrCodeData,
  isInOverlay: Boolean,
  overlayMaxWidth: Dp?,
  boundsTransform: BoundsTransform,
  sharedTransitionScope: SharedTransitionScope,
  animatedVisibilityScope: AnimatedVisibilityScope
) {
  with(sharedTransitionScope) {
    QrCode(
      data = qrCodeData,
      foregroundColor = colorResource(org.signal.core.ui.R.color.signal_light_colorPrimary),
      modifier = Modifier
        .sharedElement(
          sharedContentState = rememberSharedContentState("qr_code_display"),
          animatedVisibilityScope = animatedVisibilityScope,
          boundsTransform = boundsTransform
        )
        .size(getQrCodeSize(isInOverlay, overlayMaxWidth))
    )
  }
}

@Composable
private fun QrCodeLoading() {
  CircularProgressIndicator(
    color = colorResource(org.signal.core.ui.R.color.signal_light_colorPrimary),
    modifier = Modifier.size(48.dp)
  )
}

@Composable
private fun QrCodeScanned() {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = spacedBy(8.dp)
  ) {
    Icon(
      imageVector = SignalIcons.CheckCircle.imageVector,
      contentDescription = null,
      tint = colorResource(org.signal.core.ui.R.color.signal_light_colorPrimary),
      modifier = Modifier.size(48.dp)
    )
    Text(
      text = stringResource(R.string.LinkAccountScreen__scanned),
      style = MaterialTheme.typography.bodyMedium,
      color = colorResource(org.signal.core.ui.R.color.signal_light_colorOnSurfaceVariant)
    )
  }
}

@Composable
private fun QrCodeFailed(
  onEvent: (LinkAccountScreenEvent) -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = spacedBy(8.dp)
  ) {
    Icon(
      imageVector = SignalIcons.ErrorCircle.imageVector,
      contentDescription = null,
      tint = colorResource(org.signal.core.ui.R.color.signal_light_colorError),
      modifier = Modifier.size(48.dp)
    )
    Text(
      text = stringResource(R.string.LinkAccountScreen__failed_to_generate_code),
      style = MaterialTheme.typography.bodyMedium,
      color = colorResource(org.signal.core.ui.R.color.signal_light_colorError)
    )
    Buttons.Small(onClick = { onEvent(LinkAccountScreenEvent.RetryQrCode) }) {
      Text(text = stringResource(R.string.LinkAccountScreen__retry))
    }
  }
}

@Composable
private fun QrCodeOverlay(
  state: LinkAccountScreenState,
  onEvent: (LinkAccountScreenEvent) -> Unit,
  isPhone: Boolean
) {
  Surface(
    modifier = Modifier.fillMaxSize()
  ) {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
    ) {
      QrCodeContent(
        state = state,
        onEvent = onEvent,
        modifier = Modifier.align(Alignment.Center),
        isPhone = isPhone,
        isInOverlay = true,
        overlayMaxWidth = maxWidth
      )

      IconButtons.IconButton(
        onClick = { onEvent(LinkAccountScreenEvent.HideOverlayClick) },
        modifier = Modifier.testTag(TestTags.LINK_ACCOUNT_HIDE_OVERLAY_BUTTON)
      ) {
        Icon(
          imageVector = SignalIcons.X.imageVector,
          contentDescription = stringResource(R.string.LinkAccountScreen__close_qr_code)
        )
      }
    }
  }
}

@Composable
private fun getQrOuterBorderSize(isInOverlay: Boolean, overlayMaxWidth: Dp? = null): Dp {
  if (isInOverlay) {
    return overlayOuterBorderSize(overlayMaxWidth)
  }

  val breakpoint = rememberWindowBreakpoint()
  return when (breakpoint) {
    is WindowBreakpoint.Small -> 272.dp
    is WindowBreakpoint.Medium -> 296.dp
    is WindowBreakpoint.Large -> 364.dp
  }
}

@Composable
private fun getQrInnerBorderSize(isInOverlay: Boolean, overlayMaxWidth: Dp? = null): Dp {
  if (isInOverlay) {
    return overlayOuterBorderSize(overlayMaxWidth) * (360f / 456f)
  }

  val breakpoint = rememberWindowBreakpoint()
  return when (breakpoint) {
    is WindowBreakpoint.Small -> 208.dp
    is WindowBreakpoint.Medium -> 232.dp
    is WindowBreakpoint.Large -> 284.dp
  }
}

@Composable
private fun getQrCodeSize(isInOverlay: Boolean, overlayMaxWidth: Dp? = null): Dp {
  if (isInOverlay) {
    return overlayOuterBorderSize(overlayMaxWidth) * (297f / 456f)
  }

  val breakpoint = rememberWindowBreakpoint()
  return when (breakpoint) {
    is WindowBreakpoint.Small -> 176.dp
    is WindowBreakpoint.Medium -> 208.dp
    is WindowBreakpoint.Large -> 256.dp
  }
}

private fun overlayOuterBorderSize(overlayMaxWidth: Dp?): Dp {
  overlayMaxWidth ?: return 456.dp
  return (overlayMaxWidth - OVERLAY_HORIZONTAL_PADDING * 2).coerceAtMost(456.dp)
}

@Composable
private fun OnePaneFooterContent(
  params: RegistrationScaffold.Params.OnePane,
  isElevated: Boolean,
  onEvent: (LinkAccountScreenEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  RegistrationScaffold.FooterSurface(
    isElevated = isElevated
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = modifier
        .fillMaxWidth()
        .padding(params.footerPadding)
    ) {
      Row {
        DontHaveSignal()
      }
      CreateAccount(onEvent)
    }
  }
}

@Composable
private fun TwoPaneFooterContent(
  params: RegistrationScaffold.Params.TwoPane,
  isElevated: Boolean,
  onEvent: (LinkAccountScreenEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  RegistrationScaffold.FooterSurface(
    isElevated = isElevated
  ) {
    Row(
      horizontalArrangement = spacedBy(8.dp),
      modifier = modifier.padding(params.footerPadding)
    ) {
      Spacer(modifier = Modifier.weight(1f))

      DontHaveSignal()
      CreateAccount(onEvent)

      Spacer(modifier = Modifier.weight(1f))
    }
  }
}

@Composable
private fun DontHaveSignal() {
  Icon(
    imageVector = SignalIcons.DevicePhone.imageVector,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
    contentDescription = null
  )

  Text(
    text = stringResource(R.string.LinkAccountScreen__dont_have_signal_on_another_device),
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}

@Composable
private fun CreateAccount(onEvent: (LinkAccountScreenEvent) -> Unit) {
  Text(
    text = buildAnnotatedString {
      withLink(
        LinkAnnotation.Clickable(
          tag = "create-account",
          styles = TextLinkStyles(
            style = SpanStyle(
              color = MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.Bold,
              textDecoration = TextDecoration.Underline
            )
          ),
          linkInteractionListener = {
            onEvent(LinkAccountScreenEvent.CreateAccountClick)
          }
        )
      ) {
        append(stringResource(R.string.LinkAccountScreen__create_account))
      }
    },
    modifier = Modifier.testTag(TestTags.LINK_ACCOUNT_CREATE_ACCOUNT_LINK)
  )
}

@AllDevicePreviews
@Composable
private fun LinkAccountScreenPreview() {
  var displayQrOverlay by remember { mutableStateOf(false) }

  Previews.Preview {
    LinkAccountScreen(
      state = LinkAccountScreenState(
        qrCodeState = QrState.Loaded(qrCodeData = QrCodeData.forData("sgnl://rereg?uuid=test&pub_key=test", true)),
        displayQrOverlay = displayQrOverlay
      ),
      onEvent = {
        when (it) {
          LinkAccountScreenEvent.CreateAccountClick -> Unit
          LinkAccountScreenEvent.DisplayOverlayClick -> displayQrOverlay = true
          LinkAccountScreenEvent.GetHelpClick -> Unit
          LinkAccountScreenEvent.HideOverlayClick -> displayQrOverlay = false
          LinkAccountScreenEvent.RetryQrCode -> Unit
          LinkAccountScreenEvent.DismissError -> Unit
          LinkAccountScreenEvent.ConfirmDeleteAndRelink -> Unit
          LinkAccountScreenEvent.CancelDeleteAndRelink -> Unit
        }
      }
    )
  }
}
