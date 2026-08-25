/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.authenticatorsetup

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
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.theme.SignalTheme

@VisibleForTesting
object AuthenticatorSetupTestTags {
  const val SCROLLER = "scroller"
  const val BUTTON_OPEN = "button-open"
  const val BUTTON_COPY = "button-copy"
  const val BUTTON_CONTINUE = "button-continue"
  const val SETUP_KEY = "setup-key"
}

/**
 * Walks the user through pairing an authenticator app with their account, ending in the code entry screen.
 */
@Composable
fun AuthenticatorSetupScreen(
  state: AuthenticatorSetupState,
  onEvent: (AuthenticatorSetupEvent) -> Unit
) {
  Scaffolds.Settings(
    title = stringResource(R.string.AuthenticatorSetupScreen__authenticator_app),
    onNavigationClick = { onEvent(AuthenticatorSetupEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.X.imageVector
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
          .testTag(AuthenticatorSetupTestTags.SCROLLER)
      ) {
        TextWithLearnMore(
          text = stringResource(R.string.AuthenticatorSetupScreen__follow_these_steps),
          modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        StepCard(
          title = stringResource(R.string.AuthenticatorSetupScreen__step_1),
          body = stringResource(R.string.AuthenticatorSetupScreen__install_a_trusted_authenticator_app),
          illustration = {
            StepImage(
              image = R.drawable.image_authenticator_install_app,
              width = 48.dp,
              height = 52.dp
            )
          }
        )

        StepCard(
          title = stringResource(R.string.AuthenticatorSetupScreen__step_2),
          body = stringResource(R.string.AuthenticatorSetupScreen__open_your_authenticator_app),
          illustration = {
            StepImage(
              image = R.drawable.image_authenticator_open_app,
              width = 45.dp,
              height = 86.dp
            )
          }
        ) {
          SurfaceButton(
            text = stringResource(R.string.AuthenticatorSetupScreen__open),
            icon = SignalIcons.Open,
            onClick = { onEvent(AuthenticatorSetupEvent.OpenAuthenticatorAppClicked) },
            modifier = Modifier
              .padding(top = 16.dp)
              .testTag(AuthenticatorSetupTestTags.BUTTON_OPEN)
          )

          HorizontalDivider(
            thickness = 1.5.dp,
            color = SignalTheme.colors.colorSurface5,
            modifier = Modifier.padding(top = 24.dp)
          )

          Text(
            text = stringResource(R.string.AuthenticatorSetupScreen__or_you_can_copy_this_key),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp)
          )

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
              .testTag(AuthenticatorSetupTestTags.SETUP_KEY)
          )

          SurfaceButton(
            text = stringResource(R.string.AuthenticatorSetupScreen__copy),
            icon = SignalIcons.Copy,
            onClick = { onEvent(AuthenticatorSetupEvent.CopyKeyClicked) },
            modifier = Modifier
              .padding(top = 16.dp)
              .testTag(AuthenticatorSetupTestTags.BUTTON_COPY)
          )
        }

        StepCard(
          title = stringResource(R.string.AuthenticatorSetupScreen__step_3),
          body = stringResource(R.string.AuthenticatorSetupScreen__copy_the_code_thats_generated),
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
        onClick = { onEvent(AuthenticatorSetupEvent.ContinueClicked) },
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 40.dp, vertical = 16.dp)
          .testTag(AuthenticatorSetupTestTags.BUTTON_CONTINUE)
      ) {
        Text(text = stringResource(R.string.AuthenticatorSetupScreen__continue))
      }
    }
  }
}

/**
 * Body text with a "Learn more" link appended, which has nowhere to go yet.
 */
@Composable
private fun TextWithLearnMore(
  text: String,
  modifier: Modifier = Modifier
) {
  val learnMore = stringResource(R.string.AuthenticatorSetupScreen__learn_more)
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
  modifier: Modifier = Modifier
) {
  Buttons.MediumTonal(
    onClick = onClick,
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
private fun AuthenticatorSetupScreenPreview() {
  Previews.Preview {
    AuthenticatorSetupScreen(
      state = AuthenticatorSetupState(setupKey = "KVZ7WL3FDDWJZMTOB7PLZPKVRFD4LYSX"),
      onEvent = {}
    )
  }
}
