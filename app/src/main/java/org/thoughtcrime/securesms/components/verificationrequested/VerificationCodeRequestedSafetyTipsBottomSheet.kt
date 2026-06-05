/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.verificationrequested

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
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
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity

/**
 * Sheet showing safety tips related to a verification code alert.
 */
class VerificationCodeRequestedSafetyTipsBottomSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 1f

  companion object {
    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      VerificationCodeRequestedSafetyTipsBottomSheet().show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  @Composable
  override fun SheetContent() {
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    val scrollModifier = Modifier.nestedScroll(nestedScrollInterop)

    SafetyTipsContent(
      onOpenAccountSettings = {
        startActivity(AppSettingsActivity.account(requireContext()))
        dismissAllowingStateLoss()
      },
      modifier = scrollModifier
    )
  }
}

@Composable
private fun SafetyTipsContent(
  onOpenAccountSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier.fillMaxWidth()
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.fillMaxWidth()
    ) {
      BottomSheets.Handle()
    }

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = modifier
        .weight(weight = 1f, fill = false)
        .verticalScroll(state = scrollState)
        .padding(horizontal = 36.dp)
        .padding(bottom = 36.dp)
    ) {
      Spacer(modifier = Modifier.height(26.dp))

      Text(
        text = stringResource(id = R.string.SafetyTipsBottomSheet__title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(36.dp))

      SafetyTipRow(
        iconRes = R.drawable.safetytip_48_message,
        titleRes = R.string.SafetyTipsBottomSheet__tip_1_title,
        bodyRes = R.string.SafetyTipsBottomSheet__tip_1_body
      )

      Spacer(modifier = Modifier.height(40.dp))

      SafetyTipRow(
        iconRes = R.drawable.safetytip_48_pin,
        titleRes = R.string.SafetyTipsBottomSheet__tip_2_title,
        bodyRes = R.string.SafetyTipsBottomSheet__tip_2_body
      )

      Spacer(modifier = Modifier.height(40.dp))

      SafetyTipRow(
        iconRes = R.drawable.safetytip_48_lock,
        titleRes = R.string.SafetyTipsBottomSheet__tip_3_title,
        bodyRes = R.string.SafetyTipsBottomSheet__tip_3_body
      )

      Spacer(modifier = Modifier.height(40.dp))

      Buttons.LargeTonal(
        onClick = onOpenAccountSettings,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp)
      ) {
        Text(text = stringResource(id = R.string.SafetyTipsBottomSheet__open_account_settings))
      }
    }
  }
}

@Composable
private fun SafetyTipRow(
  iconRes: Int,
  titleRes: Int,
  bodyRes: Int
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(24.dp)
  ) {
    Image(
      painter = painterResource(id = iconRes),
      contentDescription = null,
      modifier = Modifier.size(48.dp)
    )

    Column(modifier = Modifier.fillMaxWidth()) {
      Text(
        text = stringResource(id = titleRes),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = stringResource(id = bodyRes),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun SafetyTipsContentPreview() {
  Previews.BottomSheetContentPreview {
    SafetyTipsContent(onOpenAccountSettings = {})
  }
}
