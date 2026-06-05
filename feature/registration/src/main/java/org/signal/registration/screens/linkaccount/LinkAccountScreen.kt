/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.linkaccount

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllDevicePreviews
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
import org.signal.registration.screens.quickrestore.QrState
import org.signal.registration.test.TestTags

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

  Surface(modifier = modifier.testTag(TestTags.LINK_ACCOUNT_SCREEN)) {
    SharedTransitionLayout {
      AnimatedContent(
        targetState = state.displayQrOverlay,
        label = "qr_code_fullscreen_transition"
      ) { target ->
        CompositionLocalProvider(
          LocalSharedTransitionScope provides this@SharedTransitionLayout,
          LocalAnimateVisibilityScope provides this
        ) {
          if (target) {
            QrCodeOverlay(state, onEvent)
          } else {
            when (layoutParams) {
              is RegistrationScaffold.Params.OnePane -> OnePane(layoutParams, state, onEvent)
              is RegistrationScaffold.Params.TwoPane -> TwoPane(layoutParams, state, onEvent)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun OnePane(
  params: RegistrationScaffold.Params.OnePane,
  state: LinkAccountScreenState,
  onEvent: (LinkAccountScreenEvent) -> Unit
) {
  OnePaneRegistrationScaffold(
    params = params,
    footer = { OnePaneFooterContent(onEvent = onEvent) }
  ) {
    val scroller = rememberScrollState()
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = spacedBy(64.dp),
      modifier = Modifier
        .padding(it)
        .verticalScroll(scroller)
    ) {
      Title()

      QrCodeContent(state = state, onEvent = onEvent)

      Steps(verticalArrangement = spacedBy(32.dp), onEvent)
    }
  }
}

@Composable
private fun TwoPane(
  params: RegistrationScaffold.Params.TwoPane,
  state: LinkAccountScreenState,
  onEvent: (LinkAccountScreenEvent) -> Unit
) {
  TwoPaneRegistrationScaffold(
    params = params,
    firstPane = {
      FirstPaneContent(
        onEvent = onEvent,
        modifier = Modifier
          .padding(it)
          .weight(1f)
      )
    },
    secondPane = {
      QrCodeContent(
        state = state,
        onEvent = onEvent,
        modifier = Modifier
          .weight(1f)
          .padding(it)
      )
    },
    footer = { TwoPaneFooterContent(onEvent = onEvent) }
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

    Steps(verticalArrangement = spacedBy(32.dp), onEvent = onEvent)
  }
}

@Composable
private fun Title() {
  Text(
    text = stringResource(R.string.LinkAccountScreen__scan_this_code_to_link_your_account),
    style = MaterialTheme.typography.headlineLarge
  )
}

@Composable
private fun Steps(
  verticalArrangement: Arrangement.Vertical,
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

    Step(
      icon = SignalIcons.QrCode.imageVector,
      text = stringResource(R.string.LinkAccountScreen__position_the_camera_over_this_qr_code)
    )

    GetHelp(onEvent)
  }
}

@Composable
private fun GetHelp(
  onEvent: (LinkAccountScreenEvent) -> Unit
) {
  TextButton(
    onClick = { onEvent(LinkAccountScreenEvent.GetHelpClick) },
    modifier = Modifier.testTag(TestTags.LINK_ACCOUNT_GET_HELP_BUTTON)
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
  modifier: Modifier = Modifier
) {
  val sharedTransitionScope = LocalSharedTransitionScope.current!!
  val animatedVisibilityScope = LocalAnimateVisibilityScope.current!!

  Box(
    contentAlignment = if (!state.displayQrOverlay) {
      Alignment.CenterEnd
    } else Alignment.Center,
    modifier = modifier
  ) {
    with(sharedTransitionScope) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .sharedElement(
            sharedContentState = rememberSharedContentState("qr_code_outer_border"),
            animatedVisibilityScope = animatedVisibilityScope
          )
          .size(getQrOuterBorderSize(state.displayQrOverlay))
          .background(color = colorResource(org.signal.core.ui.R.color.signal_light_colorPrimary), shape = RoundedCornerShape(64.dp))
      ) {
        AnimatedContent(
          targetState = state.qrCodeState,
          modifier = Modifier
            .sharedElement(
              sharedContentState = rememberSharedContentState("qr_code_inner_border"),
              animatedVisibilityScope = animatedVisibilityScope
            )
            .size(getQrInnerBorderSize(state.displayQrOverlay))
            .background(color = Color.White, shape = RoundedCornerShape(24.dp))
        ) { target ->
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
          ) {
            when (target) {
              QrState.Failed -> QrCodeFailed()
              is QrState.Loaded -> QrCodeDisplay(target.qrCodeData, state.displayQrOverlay, sharedTransitionScope, animatedVisibilityScope)
              QrState.Loading -> QrCodeLoading()
              QrState.Scanned -> QrCodeScanned()
            }
          }
        }
      }
    }

    AnimatedVisibility(
      visible = state.qrCodeState is QrState.Loaded && !state.displayQrOverlay,
      modifier = Modifier.align(Alignment.TopEnd),
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      IconButtons.IconButton(
        onClick = { onEvent(LinkAccountScreenEvent.DisplayOverlayClick) },
        size = 53.dp,
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
          animatedVisibilityScope = animatedVisibilityScope
        )
        .size(getQrCodeSize(isInOverlay))
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
private fun QrCodeFailed() {
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
  }
}

@Composable
fun QrCodeOverlay(
  state: LinkAccountScreenState,
  onEvent: (LinkAccountScreenEvent) -> Unit
) {
  Surface(
    modifier = Modifier.fillMaxSize()
  ) {
    Box {
      QrCodeContent(
        state = state,
        onEvent = onEvent,
        modifier = Modifier.align(Alignment.Center)
      )

      IconButtons.IconButton(
        onClick = { onEvent(LinkAccountScreenEvent.HideOverlayClick) },
        modifier = Modifier.testTag(TestTags.LINK_ACCOUNT_HIDE_OVERLAY_BUTTON)
      ) {
        Icon(imageVector = SignalIcons.X.imageVector, contentDescription = null) // TODO 'close'
      }
    }
  }
}

@Composable
fun getQrOuterBorderSize(isInOverlay: Boolean): Dp {
  if (isInOverlay) {
    return 456.dp
  }

  val breakpoint = rememberWindowBreakpoint()
  return when (breakpoint) {
    is WindowBreakpoint.Small -> 296.dp
    is WindowBreakpoint.Medium -> 296.dp
    is WindowBreakpoint.Large -> 364.dp
  }
}

@Composable
fun getQrInnerBorderSize(isInOverlay: Boolean): Dp {
  if (isInOverlay) {
    return 360.dp
  }

  val breakpoint = rememberWindowBreakpoint()
  return when (breakpoint) {
    is WindowBreakpoint.Small -> 232.dp
    is WindowBreakpoint.Medium -> 232.dp
    is WindowBreakpoint.Large -> 284.dp
  }
}

@Composable
fun getQrCodeSize(isInOverlay: Boolean): Dp {
  if (isInOverlay) {
    return 297.dp
  }

  val breakpoint = rememberWindowBreakpoint()
  return when (breakpoint) {
    is WindowBreakpoint.Small -> 208.dp
    is WindowBreakpoint.Medium -> 208.dp
    is WindowBreakpoint.Large -> 256.dp
  }
}

@Composable
private fun OnePaneFooterContent(
  onEvent: (LinkAccountScreenEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .padding(start = 36.dp, end = 36.dp, bottom = 16.dp)
      .fillMaxWidth()
  ) {
    Row {
      DontHaveSignal()
    }
    CreateAccount(onEvent)
  }
}

@Composable
private fun TwoPaneFooterContent(
  onEvent: (LinkAccountScreenEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    horizontalArrangement = spacedBy(8.dp),
    modifier = modifier.padding(36.dp)
  ) {
    Spacer(modifier = Modifier.weight(1f))

    DontHaveSignal()
    CreateAccount(onEvent)

    Spacer(modifier = Modifier.weight(1f))
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
        }
      }
    )
  }
}
