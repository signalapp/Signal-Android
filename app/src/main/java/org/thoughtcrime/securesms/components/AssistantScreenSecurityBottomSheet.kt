/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.signal.core.ui.initializeScreenshotSecurity
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Tells the user that screen security was turned on because an assistant app read the screen, with the option to turn it back off.
 */
class AssistantScreenSecurityBottomSheet : ComposeBottomSheetDialogFragment() {

  companion object {
    private val TAG = Log.tag(AssistantScreenSecurityBottomSheet::class)
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
      onReEnableScreenshotsClick = {
        Log.i(TAG, "User re-enabled screenshots.")
        SignalStore.settings.isScreenSecurityEnabled = false
        SignalStore.settings.assistantScreenSecurityDeclinedAt = System.currentTimeMillis()
        requireActivity().window.initializeScreenshotSecurity()
        dismissAllowingStateLoss()
      }
    )
  }
}

@Composable
private fun AssistantScreenSecuritySheetContent(
  onOkClick: () -> Unit = {},
  onReEnableScreenshotsClick: () -> Unit = {}
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
  ) {
    BottomSheets.Handle()

    Text(
      text = stringResource(R.string.AssistantScreenSecurityBottomSheet__body),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 24.dp)
    )

    Buttons.LargeTonal(
      onClick = onOkClick,
      modifier = Modifier
        .padding(top = 40.dp)
        .defaultMinSize(minWidth = 220.dp)
    ) {
      Text(text = stringResource(android.R.string.ok))
    }

    TextButton(
      onClick = onReEnableScreenshotsClick,
      modifier = Modifier.padding(top = 8.dp)
    ) {
      Text(text = stringResource(R.string.AssistantScreenSecurityBottomSheet__re_enable_screenshots))
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
