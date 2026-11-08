/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signallogininfo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.dismissWithAnimation
import org.signal.registration.R
import org.signal.registration.test.TestTags

/**
 * Sheet shown once the password manager has taken the user's Signal Login, warning them that they're about to be asked
 * to fill it back in so we can check it really got stored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmLoginSavedToPasswordManagerBottomSheet(
  maxButtonWidth: Dp,
  onConfirm: () -> Unit,
  onSeeLoginInfoAgain: () -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState()
  val scope = rememberCoroutineScope()

  BottomSheets.BottomSheet(
    onDismissRequest = { sheetState.dismissWithAnimation(scope, onComplete = onDismiss) },
    sheetState = sheetState
  ) {
    ConfirmLoginSavedToPasswordManagerBottomSheetContent(
      maxButtonWidth = maxButtonWidth,
      onConfirmClick = { sheetState.dismissWithAnimation(scope, onComplete = onConfirm) },
      onSeeLoginInfoAgainClick = { sheetState.dismissWithAnimation(scope, onComplete = onSeeLoginInfoAgain) }
    )
  }
}

@Composable
private fun ConfirmLoginSavedToPasswordManagerBottomSheetContent(
  maxButtonWidth: Dp,
  onConfirmClick: () -> Unit,
  onSeeLoginInfoAgainClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp)
      .padding(top = 24.dp, bottom = 16.dp)
      .testTag(TestTags.CONFIRM_LOGIN_SAVED_TO_PASSWORD_MANAGER_SHEET)
  ) {
    Image(
      painter = painterResource(R.drawable.image_signal_login_lock_checkmark),
      contentDescription = null,
      modifier = Modifier.size(96.dp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = stringResource(R.string.ConfirmLoginSavedToPasswordManagerBottomSheet__confirm_your_login_info_is_saved),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = stringResource(R.string.ConfirmLoginSavedToPasswordManagerBottomSheet__confirm_that_your_login_info_was_saved),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(36.dp))

    Buttons.LargeTonal(
      onClick = onConfirmClick,
      colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
      ),
      modifier = Modifier
        .widthIn(max = maxButtonWidth)
        .fillMaxWidth()
        .testTag(TestTags.CONFIRM_LOGIN_SAVED_TO_PASSWORD_MANAGER_CONFIRM_BUTTON)
    ) {
      Text(stringResource(R.string.ConfirmLoginSavedToPasswordManagerBottomSheet__confirm_login_info))
    }

    TextButton(
      onClick = onSeeLoginInfoAgainClick,
      modifier = Modifier
        .widthIn(max = maxButtonWidth)
        .fillMaxWidth()
        .testTag(TestTags.CONFIRM_LOGIN_SAVED_TO_PASSWORD_MANAGER_SEE_LOGIN_INFO_AGAIN_BUTTON)
    ) {
      Text(stringResource(R.string.ConfirmLoginSavedToPasswordManagerBottomSheet__see_login_info_again))
    }
  }
}

@Preview
@Composable
private fun ConfirmLoginSavedToPasswordManagerBottomSheetPreview() {
  Previews.BottomSheetPreview {
    ConfirmLoginSavedToPasswordManagerBottomSheetContent(
      maxButtonWidth = 320.dp,
      onConfirmClick = {},
      onSeeLoginInfoAgainClick = {}
    )
  }
}
