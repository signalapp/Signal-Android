/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.notifications

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment

private const val PLACEHOLDER = "__TOGGLE_PLACEHOLDER__"

/**
 * Sheet explaining how to turn on notifications and providing an action to do so.
 */
class TurnOnNotificationsBottomSheet : ComposeBottomSheetDialogFragment() {

  @Composable
  override fun SheetContent() {
    TurnOnNotificationsSheetContent(this::goToSystemNotificationSettings)
  }

  private fun goToSystemNotificationSettings() {
    if (Build.VERSION.SDK_INT >= 26 && !NotificationChannels.getInstance().isMessageChannelEnabled) {
      val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
      intent.putExtra(Settings.EXTRA_CHANNEL_ID, NotificationChannels.getInstance().messagesChannel)
      intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
      startActivity(intent)
    } else if (Build.VERSION.SDK_INT >= 26 && (!NotificationChannels.getInstance().areNotificationsEnabled() || !NotificationChannels.getInstance().isMessagesChannelGroupEnabled)) {
      val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
      intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
      startActivity(intent)
    } else {
      startActivity(AppSettingsActivity.notifications(requireContext()))
    }

    dismissAllowingStateLoss()
  }
}

@Preview
@Composable
private fun TurnOnNotificationsSheetContentPreview() {
  SignalTheme(isDarkMode = false) {
    Surface {
      TurnOnNotificationsSheetContent {}
    }
  }
}

@Composable
private fun TurnOnNotificationsSheetContent(
  onGoToSettingsClicked: () -> Unit
) {
  Column(
    modifier = Modifier
      .padding(horizontal = dimensionResource(id = R.dimen.core_ui__gutter))
      .padding(bottom = 32.dp)
  ) {
    BottomSheets.Handle(
      modifier = Modifier.align(Alignment.CenterHorizontally)
    )

    Text(
      text = stringResource(R.string.TurnOnNotificationsBottomSheet__turn_on_notifications),
      style = MaterialTheme.typography.headlineSmall,
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .padding(bottom = 12.dp, top = 10.dp)
    )

    Text(
      text = stringResource(R.string.TurnOnNotificationsBottomSheet__to_receive_notifications),
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .padding(bottom = 32.dp)
    )

    Text(
      text = stringResource(R.string.TurnOnNotificationsBottomSheet__1_tap_settings_below),
      modifier = Modifier.padding(bottom = 32.dp)
    )

    val step2String = stringResource(id = R.string.TurnOnNotificationsBottomSheet__2_s_turn_on_notifications, PLACEHOLDER)
    val (step2Text, step2InlineContent) = remember(step2String) {
      val parts = step2String.split(PLACEHOLDER)
      val annotatedString = buildAnnotatedString {
        append(parts[0])
        appendInlineContent("toggle")
        append(parts[1])
      }

      val inlineContentMap = mapOf(
        "toggle" to InlineTextContent(Placeholder(36.sp, 22.sp, PlaceholderVerticalAlign.Center)) {
          Image(
            imageVector = ImageVector.vectorResource(id = R.drawable.illustration_toggle_switch),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
          )
        }
      )

      annotatedString to inlineContentMap
    }

    Text(
      text = step2Text,
      inlineContent = step2InlineContent,
      modifier = Modifier.padding(bottom = 32.dp)
    )

    Buttons.LargeTonal(
      onClick = onGoToSettingsClicked,
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .fillMaxWidth(1f)
    ) {
      Text(text = stringResource(id = R.string.TurnOnNotificationsBottomSheet__settings))
    }
  }
}
