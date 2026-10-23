/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.passkeysetup

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import org.signal.appsettings.R
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons

@VisibleForTesting
object PasskeySetupTestTags {
  const val SCROLLER = "scroller"
  const val LINK_LEARN_MORE = "link-learn-more"
  const val BUTTON_SET_UP = "button-set-up"
  const val DIALOG_MAX_TWO_FACTOR_METHODS_REACHED = "dialog-max-two-factor-methods-reached"
}

/**
 * Explains what a passkey is before the user sets one up, and runs the ceremony when they decide to.
 */
@Composable
fun PasskeySetupScreen(
  state: PasskeySetupState,
  onEvent: (PasskeySetupEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = stringResource(R.string.PasskeySetupScreen__passkeys),
    onNavigationClick = { onEvent(PasskeySetupEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    modifier = modifier
  ) { contentPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
          .testTag(PasskeySetupTestTags.SCROLLER)
      ) {
        Image(
          painter = painterResource(R.drawable.image_passkey_setup),
          contentDescription = null,
          modifier = Modifier
            .padding(top = 24.dp)
            .size(width = 64.dp, height = 96.dp)
        )

        Description(
          onEvent = onEvent,
          modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 20.dp)
        )

        Column(
          verticalArrangement = Arrangement.spacedBy(24.dp),
          modifier = Modifier.padding(start = 64.dp, end = 64.dp, top = 40.dp, bottom = 24.dp)
        ) {
          Bullet(
            icon = SignalIcons.CheckCircle,
            text = stringResource(R.string.PasskeySetupScreen__give_your_passkey_a_friendly_name)
          )

          Bullet(
            icon = SignalIcons.Lock,
            text = stringResource(R.string.PasskeySetupScreen__use_your_devices_biometrics)
          )

          Bullet(
            icon = SignalIcons.Trash,
            text = stringResource(R.string.PasskeySetupScreen__add_or_remove_passkeys_at_any_time)
          )
        }
      }

      Buttons.MediumTonal(
        onClick = { onEvent(PasskeySetupEvent.SetUpPasskeyClicked) },
        colors = ButtonDefaults.filledTonalButtonColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 40.dp, vertical = 16.dp)
          .testTag(PasskeySetupTestTags.BUTTON_SET_UP)
      ) {
        Text(text = stringResource(R.string.PasskeySetupScreen__set_up_a_passkey))
      }
    }

    when (val dialog = state.dialog) {
      PasskeySetupState.Dialog.None -> Unit
      PasskeySetupState.Dialog.InProgress -> Dialogs.IndeterminateProgressDialog()
      is PasskeySetupState.Dialog.MaxTwoFactorMethodsReached -> MaxTwoFactorMethodsReachedDialog(dialog = dialog, onEvent = onEvent)
    }
  }
}

/**
 * The explanation of what a passkey is, with a "Learn more" link on its own line underneath.
 */
@Composable
private fun Description(
  onEvent: (PasskeySetupEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Text(
    text = buildAnnotatedString {
      append(stringResource(R.string.PasskeySetupScreen__with_passkeys_you_can_easily_add))
      append("\n")

      withLink(
        LinkAnnotation.Clickable(
          tag = "learn-more",
          styles = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary)),
          linkInteractionListener = { onEvent(PasskeySetupEvent.LearnMoreClicked) }
        )
      ) {
        append(stringResource(R.string.PasskeySetupScreen__learn_more))
      }
    },
    style = MaterialTheme.typography.bodyLarge.copy(hyphens = Hyphens.Auto),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = modifier.testTag(PasskeySetupTestTags.LINK_LEARN_MORE)
  )
}

@Composable
private fun Bullet(
  icon: SignalIcons,
  text: String
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Icon(
      painter = icon.painter,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun MaxTwoFactorMethodsReachedDialog(
  dialog: PasskeySetupState.Dialog.MaxTwoFactorMethodsReached,
  onEvent: (PasskeySetupEvent) -> Unit
) {
  Dialogs.SimpleAlertDialog(
    title = stringResource(R.string.PasskeySetupScreen__cant_add_passkey),
    body = stringResource(R.string.PasskeySetupScreen__you_cant_add_more_than_d_two_factor_methods, dialog.maxTwoFactorMethods),
    confirm = stringResource(android.R.string.ok),
    onConfirm = {},
    onDismiss = { onEvent(PasskeySetupEvent.DialogDismissed) },
    dismiss = stringResource(R.string.PasskeySetupScreen__learn_more),
    onDeny = { onEvent(PasskeySetupEvent.LearnMoreClicked) },
    onDismissRequest = { onEvent(PasskeySetupEvent.DialogDismissed) },
    modifier = Modifier.testTag(PasskeySetupTestTags.DIALOG_MAX_TWO_FACTOR_METHODS_REACHED)
  )
}

@DayNightPreviews
@Composable
private fun PasskeySetupScreenPreview() {
  Previews.Preview {
    PasskeySetupScreen(
      state = PasskeySetupState(),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun PasskeySetupScreenInProgressPreview() {
  Previews.Preview {
    PasskeySetupScreen(
      state = PasskeySetupState(dialog = PasskeySetupState.Dialog.InProgress),
      onEvent = {}
    )
  }
}

@DayNightPreviews
@Composable
private fun PasskeySetupScreenMaxTwoFactorMethodsReachedPreview() {
  Previews.Preview {
    PasskeySetupScreen(
      state = PasskeySetupState(dialog = PasskeySetupState.Dialog.MaxTwoFactorMethodsReached(maxTwoFactorMethods = 10)),
      onEvent = {}
    )
  }
}
