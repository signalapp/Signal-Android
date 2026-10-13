/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.totpsetup

import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.appsettings.R
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme

@VisibleForTesting
object TotpSetupTestTags {
  const val SCROLLER = "scroller"
  const val BUTTON_OPEN = "button-open"
  const val BUTTON_COPY = "button-copy"
  const val BUTTON_CONTINUE = "button-continue"
  const val SETUP_KEY = "setup-key"
  const val SETUP_KEY_SPINNER = "setup-key-spinner"
}

/**
 * Walks the user through pairing an authenticator app with their account, ending in the code entry screen.
 */
@Composable
fun TotpSetupScreen(
  state: TotpSetupState,
  onEvent: (TotpSetupEvent) -> Unit
) {
  Scaffolds.Settings(
    title = stringResource(R.string.TotpSetupScreen__set_up_your_authenticator_app),
    onNavigationClick = { onEvent(TotpSetupEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .testTag(TotpSetupTestTags.SCROLLER)
      ) {
        TextWithLearnMore(
          text = stringResource(R.string.TotpSetupScreen__follow_these_steps),
          modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        StepCard(
          title = stringResource(R.string.TotpSetupScreen__step_1),
          body = stringResource(R.string.TotpSetupScreen__install_a_trusted_authenticator_app),
          illustration = {
            StepImage(
              image = R.drawable.image_authenticator_install_app,
              width = 48.dp,
              height = 52.dp
            )
          }
        )

        StepCard(
          title = stringResource(R.string.TotpSetupScreen__step_2),
          body = stringResource(R.string.TotpSetupScreen__open_your_authenticator_app),
          illustration = {
            StepImage(
              image = R.drawable.image_authenticator_open_app,
              width = 45.dp,
              height = 86.dp
            )
          }
        ) {
          SurfaceButton(
            text = stringResource(R.string.TotpSetupScreen__open),
            icon = SignalIcons.Open,
            enabled = state.canContinue,
            onClick = { onEvent(TotpSetupEvent.OpenTotpAppClicked) },
            modifier = Modifier
              .padding(top = 16.dp)
              .testTag(TotpSetupTestTags.BUTTON_OPEN)
          )

          HorizontalDivider(
            thickness = 1.5.dp,
            color = SignalTheme.colors.colorSurface5,
            modifier = Modifier.padding(top = 24.dp)
          )

          Text(
            text = stringResource(R.string.TotpSetupScreen__or_you_can_copy_this_key),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp)
          )

          if (state.loading) {
            CircularProgressIndicator(
              strokeWidth = 2.dp,
              modifier = Modifier
                .padding(top = 8.dp)
                .size(20.dp)
                .testTag(TotpSetupTestTags.SETUP_KEY_SPINNER)
            )
          } else {
            Text(
              text = state.setupKey,
              style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.9.sp
              ),
              modifier = Modifier
                .padding(top = 4.dp)
                .testTag(TotpSetupTestTags.SETUP_KEY)
            )
          }

          SurfaceButton(
            text = stringResource(R.string.TotpSetupScreen__copy),
            icon = SignalIcons.Copy,
            enabled = state.canContinue,
            onClick = { onEvent(TotpSetupEvent.CopyKeyClicked) },
            modifier = Modifier
              .padding(top = 16.dp)
              .testTag(TotpSetupTestTags.BUTTON_COPY)
          )
        }

        StepCard(
          title = stringResource(R.string.TotpSetupScreen__step_3),
          body = stringResource(R.string.TotpSetupScreen__copy_the_code_thats_generated),
          illustration = {
            StepImage(
              image = R.drawable.image_authenticator_copy_code,
              width = 62.dp,
              height = 38.dp
            )
          }
        )

        Spacer(modifier = Modifier.height(24.dp))
      }

      Buttons.LargeTonal(
        onClick = { onEvent(TotpSetupEvent.ContinueClicked) },
        enabled = state.canContinue,
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 40.dp, vertical = 16.dp)
          .testTag(TotpSetupTestTags.BUTTON_CONTINUE)
      ) {
        Text(text = stringResource(R.string.TotpSetupScreen__continue))
      }
    }

    SetupDialog(dialog = state.dialog, onEvent = onEvent)
  }
}

/**
 * Neither of these is recoverable on this screen, so dismissing either one leaves it.
 */
@Composable
private fun SetupDialog(
  dialog: TotpSetupState.Dialog,
  onEvent: (TotpSetupEvent) -> Unit
) {
  val message = when (dialog) {
    TotpSetupState.Dialog.None -> return
    is TotpSetupState.Dialog.MaxAppsReached -> stringResource(R.string.TotpSetupScreen__you_cant_add_more_than_d, dialog.maxApps)
    TotpSetupState.Dialog.NetworkFailure -> stringResource(R.string.TotpSetupScreen__couldnt_reach_signal)
  }

  Dialogs.SimpleMessageDialog(
    message = message,
    dismiss = stringResource(android.R.string.ok),
    onDismiss = { onEvent(TotpSetupEvent.DialogDismissed) }
  )
}

/**
 * Body text with a "Learn more" link appended, which has nowhere to go yet.
 */
@Composable
private fun TextWithLearnMore(
  text: String,
  modifier: Modifier = Modifier
) {
  val learnMore = stringResource(R.string.TotpSetupScreen__learn_more)
  val primaryColor = MaterialTheme.colorScheme.primary

  Text(
    text = remember(text, learnMore, primaryColor) {
      buildAnnotatedString {
        append(text)
        append(" ")
        withStyle(SpanStyle(color = primaryColor)) {
          append(learnMore)
        }
      }
    },
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier
  )
}

/**
 * One of the numbered steps, which is a card with a title and body alongside an illustration, plus whatever [content]
 * the step needs underneath. The illustration is centered on the title and body, and [content] runs the full width of
 * the card below both.
 */
@Composable
private fun StepCard(
  title: String,
  body: String,
  illustration: @Composable () -> Unit,
  content: @Composable ColumnScope.() -> Unit = {}
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp)
      .clip(RoundedCornerShape(24.dp))
      .background(SignalTheme.colors.colorSurface2)
      .padding(horizontal = 24.dp, vertical = 20.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall
        )

        Text(
          text = body,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 2.dp)
        )
      }

      illustration()
    }

    content()
  }
}

@Composable
private fun StepImage(
  @DrawableRes image: Int,
  width: Dp,
  height: Dp
) {
  Image(
    painter = painterResource(image),
    contentDescription = null,
    modifier = Modifier.size(width = width, height = height)
  )
}

/**
 * The pill button used inside the step cards, which sits on the card rather than on the page and so uses the surface
 * color as its background.
 */
@Composable
private fun SurfaceButton(
  text: String,
  icon: SignalIcons,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  Buttons.MediumTonal(
    onClick = onClick,
    enabled = enabled,
    colors = ButtonDefaults.filledTonalButtonColors(
      containerColor = MaterialTheme.colorScheme.surface,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ),
    modifier = modifier
  ) {
    Icon(
      painter = icon.painter,
      contentDescription = null,
      modifier = Modifier
        .padding(end = 8.dp)
        .size(20.dp)
    )

    Text(text = text)
  }
}

@DayNightPreviews
@Composable
private fun TotpSetupScreenPreview() {
  Previews.Preview {
    TotpSetupScreen(
      state = TotpSetupState(setupKey = "KVZ7 WL3F DDWJ ZMTO B7PL ZPKV RFD4 LYSX", loading = false),
      onEvent = {}
    )
  }
}
