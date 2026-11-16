/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R

/**
 * Tells the user that screen security was turned on because an assistant app read the screen, with the option to turn it back off.
 */
class AssistantScreenSecurityBottomSheet : ComposeBottomSheetDialogFragment() {

  companion object {
    private const val FRAGMENT_TAG = "AssistantScreenSecurityBottomSheet"

    fun show(fragmentManager: FragmentManager) {
      if (fragmentManager.findFragmentByTag(FRAGMENT_TAG) == null) {
        BottomSheetUtil.show(fragmentManager, FRAGMENT_TAG, AssistantScreenSecurityBottomSheet())
      }
    }
  }

  override val peekHeightPercentage: Float = 1f

  @Composable
  override fun SheetContent() {
    AssistantScreenSecuritySheetContent(
      onOkClick = { dismissAllowingStateLoss() },
      onTurnOffClick = {
        AssistantScreenSecurityTurnOffDialog.show(parentFragmentManager)
        dismissAllowingStateLoss()
      }
    )
  }
}

@Composable
private fun AssistantScreenSecuritySheetContent(
  onOkClick: () -> Unit = {},
  onTurnOffClick: () -> Unit = {}
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
  ) {
    BottomSheets.Handle()

    Image(
      painter = painterResource(R.drawable.image_assistant_screen_security),
      contentDescription = null,
      modifier = Modifier
        .padding(top = 14.dp)
        .size(96.dp)
    )

    Text(
      text = stringResource(R.string.AssistantScreenSecurityBottomSheet__title),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(top = 12.dp)
    )

    Text(
      text = stringResource(R.string.AssistantScreenSecurityBottomSheet__body),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 12.dp)
    )

    Buttons.MediumTonal(
      onClick = onOkClick,
      colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
      ),
      modifier = Modifier
        .padding(top = 48.dp)
        .defaultMinSize(minWidth = 220.dp)
    ) {
      Text(text = stringResource(android.R.string.ok))
    }

    TextButton(
      onClick = onTurnOffClick,
      modifier = Modifier.padding(top = 12.dp)
    ) {
      Text(
        text = stringResource(R.string.AssistantScreenSecurityBottomSheet__turn_off_screen_security),
        color = MaterialTheme.colorScheme.error
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun AssistantScreenSecuritySheetContentPreview() {
  Previews.BottomSheetContentPreview {
    AssistantScreenSecuritySheetContent()
  }
}
