/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package org.signal.registration.screens.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SideBySideLayout
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.dismissWithAnimation
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.rememberWindowBreakpoint
import org.signal.registration.R
import org.signal.registration.screens.RegistrationScreen
import org.signal.registration.test.TestTags

/**
 * Welcome screen for the registration flow.
 * This is the initial screen users see when starting the registration process.
 */
@Composable
fun WelcomeScreen(
  onEvent: (WelcomeScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  var showBottomSheet by remember { mutableStateOf(false) }
  val windowBreakpoint = rememberWindowBreakpoint()
  val onRestoreOrTransferClick = { showBottomSheet = true }
  val onTermsAndPrivacyClick: () -> Unit = {}

  when (windowBreakpoint) {
    WindowBreakpoint.SMALL -> {
      CompactLayout(
        onEvent = onEvent,
        onRestoreOrTransferClick = onRestoreOrTransferClick,
        onTermsAndPrivacyClick = onTermsAndPrivacyClick,
        modifier = modifier
      )
    }

    WindowBreakpoint.MEDIUM -> {
      MediumLayout(
        onEvent = onEvent,
        onRestoreOrTransferClick = onRestoreOrTransferClick,
        onTermsAndPrivacyClick = onTermsAndPrivacyClick,
        modifier = modifier
      )
    }

    WindowBreakpoint.LARGE -> {
      LargeLayout(
        onEvent = onEvent,
        onTermsAndPrivacyClick = onTermsAndPrivacyClick,
        onRestoreOrTransferClick = onRestoreOrTransferClick,
        modifier = modifier
      )
    }
  }

  if (showBottomSheet) {
    RestoreOrTransferBottomSheet(
      onEvent = {
        showBottomSheet = false
        onEvent(it)
      },
      onDismiss = { showBottomSheet = false }
    )
  }
}

@Composable
private fun CompactLayout(
  onEvent: (WelcomeScreenEvents) -> Unit,
  onTermsAndPrivacyClick: () -> Unit,
  onRestoreOrTransferClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  RegistrationScreen(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.WELCOME_SCREEN),
    content = {
      Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        HeroImage(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(16.dp)
        )

        Headline(
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))
      }
    },
    footer = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        TermsAndPrivacy(onTermsAndPrivacyClick = onTermsAndPrivacyClick)

        Spacer(modifier = Modifier.height(24.dp))

        PrimaryDeviceCallToActionButtons(
          onEvent = onEvent,
          onRestoreOrTransferClick = onRestoreOrTransferClick
        )

        Spacer(modifier = Modifier.height(48.dp))
      }
    }
  )
}

@Composable
private fun MediumLayout(
  onEvent: (WelcomeScreenEvents) -> Unit,
  onTermsAndPrivacyClick: () -> Unit,
  onRestoreOrTransferClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  RegistrationScreen(
    modifier = modifier
      .fillMaxSize()
      .padding(bottom = 56.dp),
    content = {
      SideBySideLayout(
        modifier = Modifier.fillMaxSize(),
        primary = {
          HeroImage()
        },
        secondary = {
          Headline(
            modifier = Modifier.padding(top = 88.dp)
          )
        }
      )
    },
    footer = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        TermsAndPrivacy(onTermsAndPrivacyClick = onTermsAndPrivacyClick)

        PrimaryDeviceCallToActionButtons(
          onEvent = onEvent,
          onRestoreOrTransferClick = onRestoreOrTransferClick
        )
      }
    }
  )
}

@Composable
private fun LargeLayout(
  onEvent: (WelcomeScreenEvents) -> Unit,
  onTermsAndPrivacyClick: () -> Unit,
  onRestoreOrTransferClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  RegistrationScreen(
    modifier = modifier.fillMaxSize(),
    content = {
      SideBySideLayout(
        modifier = Modifier.fillMaxSize(),
        primary = {
          HeroImage(
            modifier = Modifier.padding(vertical = 10.dp)
          )
        },
        secondary = {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              horizontalAlignment = Alignment.Start,
              modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .padding(top = 98.dp)
            ) {
              Headline(
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(start = 10.dp)
              )

              Spacer(modifier = Modifier.weight(1f))

              TermsAndPrivacy(
                onTermsAndPrivacyClick = onTermsAndPrivacyClick,
                modifier = Modifier
                  .align(Alignment.CenterHorizontally)
                  .padding(bottom = 8.dp)
              )

              PrimaryDeviceCallToActionButtons(
                onEvent = onEvent,
                onRestoreOrTransferClick = onRestoreOrTransferClick
              )
            }
          }
        }
      )
    }
  )
}

@Composable
private fun HeroImage(
  modifier: Modifier = Modifier
) {
  Image(
    painter = painterResource(R.drawable.welcome),
    contentDescription = null,
    modifier = modifier,
    contentScale = ContentScale.Fit
  )
}

@Composable
private fun Headline(
  modifier: Modifier = Modifier,
  style: TextStyle = MaterialTheme.typography.headlineMedium,
  textAlign: TextAlign = TextAlign.Start
) {
  Text(
    text = stringResource(R.string.RegistrationActivity_take_privacy_with_you_be_yourself_in_every_message),
    style = style,
    textAlign = textAlign,
    modifier = modifier
      .testTag(TestTags.WELCOME_HEADLINE)
  )
}

@Composable
private fun TermsAndPrivacy(
  onTermsAndPrivacyClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  TextButton(
    onClick = onTermsAndPrivacyClick,
    colors = ButtonDefaults.textButtonColors(
      contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ),
    modifier = modifier
  ) {
    Text(
      text = stringResource(R.string.RegistrationActivity_terms_and_privacy),
      textAlign = TextAlign.Center
    )
  }
}

@Composable
private fun ColumnScope.PrimaryDeviceCallToActionButtons(
  onEvent: (WelcomeScreenEvents) -> Unit,
  onRestoreOrTransferClick: () -> Unit
) {
  Buttons.LargeTonal(
    onClick = { onEvent(WelcomeScreenEvents.Continue) },
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 32.dp)
      .testTag(TestTags.WELCOME_GET_STARTED_BUTTON)
  ) {
    Text(stringResource(R.string.RegistrationActivity_continue))
  }

  Spacer(modifier = Modifier.height(17.dp))

  Buttons.LargeTonal(
    onClick = onRestoreOrTransferClick,
    colors = ButtonDefaults.filledTonalButtonColors(
      containerColor = SignalTheme.colors.colorSurface2
    ),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 32.dp)
      .testTag(TestTags.WELCOME_RESTORE_OR_TRANSFER_BUTTON)
  ) {
    Text(stringResource(R.string.registration_activity__restore_or_transfer))
  }
}

@Composable
private fun ColumnScope.SecondaryDeviceCallToActionButtons(
  onEvent: (WelcomeScreenEvents) -> Unit
) {
  Buttons.LargeTonal(
    onClick = { onEvent(WelcomeScreenEvents.LinkDevice) },
    modifier = Modifier
      .fillMaxWidth()
      .testTag(TestTags.WELCOME_LINK_DEVICE_BUTTON)
  ) {
    Text(stringResource(R.string.WelcomeScreen__link_your_account))
  }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.align(Alignment.CenterHorizontally)
  ) {
    Text(
      text = stringResource(R.string.WelcomeScreen__not_on_signal_yet)
    )

    TextButton(
      onClick = { onEvent(WelcomeScreenEvents.Continue) },
      modifier = Modifier.testTag(TestTags.WELCOME_GET_STARTED_BUTTON)
    ) {
      Text(
        text = stringResource(R.string.WelcomeScreen__create_account)
      )
    }
  }
}

/**
 * Bottom sheet for restore or transfer options.
 */
@Composable
private fun RestoreOrTransferBottomSheet(
  onEvent: (WelcomeScreenEvents) -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState()
  val scope = rememberCoroutineScope()

  BottomSheets.BottomSheet(
    onDismissRequest = { sheetState.dismissWithAnimation(scope, onComplete = onDismiss) },
    sheetState = sheetState
  ) {
    RestoreOrTransferBottomSheetContent(
      sheetState = sheetState,
      onEvent = onEvent,
      scope = scope
    )
  }
}

/**
 * Bottom sheet content for restore or transfer options (needs to be separate for preview).
 */
@Composable
private fun RestoreOrTransferBottomSheetContent(
  sheetState: SheetState,
  scope: CoroutineScope,
  onEvent: (WelcomeScreenEvents) -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 54.dp)
  ) {
    Spacer(modifier = Modifier.size(26.dp))

    RestoreActionRow(
      icon = SignalIcons.QrCode.painter,
      title = stringResource(R.string.WelcomeFragment_restore_action_i_have_my_old_phone),
      subtitle = stringResource(R.string.WelcomeFragment_restore_action_scan_qr),
      modifier = Modifier.testTag(TestTags.WELCOME_RESTORE_HAS_OLD_PHONE_BUTTON),
      onRowClick = {
        sheetState.dismissWithAnimation(scope) {
          onEvent(WelcomeScreenEvents.HasOldPhone)
        }
      }
    )

    RestoreActionRow(
      icon = painterResource(R.drawable.symbol_no_phone_44),
      title = stringResource(R.string.WelcomeFragment_restore_action_i_dont_have_my_old_phone),
      subtitle = stringResource(R.string.WelcomeFragment_restore_action_reinstalling),
      modifier = Modifier.testTag(TestTags.WELCOME_RESTORE_NO_OLD_PHONE_BUTTON),
      onRowClick = {
        sheetState.dismissWithAnimation(scope) {
          onEvent(WelcomeScreenEvents.DoesNotHaveOldPhone)
        }
      }
    )
  }
}

@Composable
private fun RestoreActionRow(
  icon: Painter,
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  onRowClick: () -> Unit = {}
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .horizontalGutters()
      .padding(vertical = 8.dp)
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(MaterialTheme.colorScheme.background)
      .clickable(enabled = true, onClick = onRowClick)
      .padding(horizontal = 24.dp, vertical = 16.dp)
  ) {
    Icon(
      painter = icon,
      tint = MaterialTheme.colorScheme.primary,
      contentDescription = null,
      modifier = Modifier.size(44.dp)
    )

    Column(
      modifier = Modifier.padding(start = 16.dp)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge
      )

      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@AllDevicePreviews
@Composable
private fun WelcomeScreenPreview() {
  Previews.Preview {
    WelcomeScreen(onEvent = {})
  }
}

@AllDevicePreviews
@Composable
private fun RestoreOrTransferBottomSheetPreview() {
  Previews.BottomSheetPreview {
    RestoreOrTransferBottomSheetContent(
      sheetState = rememberModalBottomSheetState(),
      scope = rememberCoroutineScope(),
      onEvent = {}
    )
  }
}
