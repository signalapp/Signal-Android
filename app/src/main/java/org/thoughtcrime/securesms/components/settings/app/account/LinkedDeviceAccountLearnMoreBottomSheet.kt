/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R

/**
 * Bottom sheet shown from the linked-device Account settings screen explaining what linked devices
 * are and that account management lives on the primary device.
 */
class LinkedDeviceAccountLearnMoreBottomSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.75f

  companion object {
    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      LinkedDeviceAccountLearnMoreBottomSheet()
        .show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  @Composable
  override fun SheetContent() {
    LinkedDeviceAccountLearnMoreSheet()
  }
}

@Composable
private fun LinkedDeviceAccountLearnMoreSheet() {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .horizontalGutters()
      .padding(bottom = 48.dp)
  ) {
    BottomSheets.Handle()

    Icon(
      imageVector = SignalIcons.Devices.imageVector,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier
        .padding(top = 16.dp, bottom = 24.dp)
        .clip(RoundedCornerShape(50))
        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        .padding(horizontal = 20.dp, vertical = 6.dp)
        .size(40.dp)
    )

    val linkedDevices = stringResource(R.string.LinkedDeviceAccountLearnMoreBottomSheet__linked_devices)
    val text = stringResource(R.string.LinkedDeviceAccountLearnMoreBottomSheet__linked_devices_let_you_access)
    val annotatedText = remember(text, linkedDevices) {
      buildAnnotatedString {
        val start = text.indexOf(linkedDevices)
        if (start >= 0) {
          append(text.substring(0, start))
          withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append(linkedDevices)
          }
          append(text.substring(start + linkedDevices.length))
        } else {
          append(text)
        }
      }
    }

    Text(
      text = annotatedText,
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 20.dp)
    )

    BulletRow(stringResource(R.string.LinkedDeviceAccountLearnMoreBottomSheet__you_can_link_a_desktop))
    BulletRow(stringResource(R.string.LinkedDeviceAccountLearnMoreBottomSheet__your_primary_device_manages))
    BulletRow(stringResource(R.string.LinkedDeviceAccountLearnMoreBottomSheet__some_account_settings))
  }
}

@Composable
private fun BulletRow(text: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 12.dp),
    verticalAlignment = Alignment.Top
  ) {
    Text(
      text = "\u2022",
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(end = 8.dp)
    )

    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge
    )
  }
}

@DayNightPreviews
@Composable
private fun LinkedDeviceAccountLearnMoreSheetPreview() {
  Previews.BottomSheetContentPreview {
    LinkedDeviceAccountLearnMoreSheet()
  }
}
