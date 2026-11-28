/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.signallogin.warning

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.compose.dismissWithAnimation
import org.signal.signallogin.R
import org.signal.signallogin.SignalLoginTestTags

/**
 * Sheet shown before the user copies or saves their Signal Login, warning them that anyone who gets hold of it can take
 * over their account. [onGotIt] is the signal to go ahead with whatever they were doing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoNotShareLoginBottomSheet(
  onGotIt: () -> Unit,
  onLearnMore: () -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()

  BottomSheets.BottomSheet(
    onDismissRequest = { sheetState.dismissWithAnimation(scope, onComplete = onDismiss) },
    sheetState = sheetState
  ) {
    DoNotShareLoginBottomSheetContent(
      onGotItClick = { sheetState.dismissWithAnimation(scope, onComplete = onGotIt) },
      onLearnMoreClick = { sheetState.dismissWithAnimation(scope, onComplete = onLearnMore) }
    )
  }
}

@Composable
private fun DoNotShareLoginBottomSheetContent(
  onGotItClick: () -> Unit,
  onLearnMoreClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp)
      .padding(top = 20.dp, bottom = 16.dp)
      .testTag(SignalLoginTestTags.DO_NOT_SHARE_SHEET)
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.symbol_error_triangle_40),
      tint = MaterialTheme.colorScheme.error,
      contentDescription = null,
      modifier = Modifier
        .size(80.dp)
        .background(color = MaterialTheme.colorScheme.errorContainer, shape = CircleShape)
        .padding(20.dp)
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = stringResource(R.string.DoNotShareLoginBottomSheet__do_not_share_your_signal_login),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = Texts.annotatedStringResource(R.string.DoNotShareLoginBottomSheet__do_not_share_your_signal_login_with_anyone),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(64.dp))

    Buttons.LargeTonal(
      onClick = onGotItClick,
      colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
      ),
      modifier = Modifier.testTag(SignalLoginTestTags.DO_NOT_SHARE_GOT_IT_BUTTON)
    ) {
      Text(stringResource(R.string.DoNotShareLoginBottomSheet__got_it))
    }

    TextButton(
      onClick = onLearnMoreClick,
      modifier = Modifier.testTag(SignalLoginTestTags.DO_NOT_SHARE_LEARN_MORE_BUTTON)
    ) {
      Text(stringResource(R.string.DoNotShareLoginBottomSheet__learn_more))
    }
  }
}

@DayNightPreviews
@Composable
private fun DoNotShareLoginBottomSheetPreview() {
  Previews.BottomSheetPreview {
    DoNotShareLoginBottomSheetContent(
      onGotItClick = {},
      onLearnMoreClick = {}
    )
  }
}
