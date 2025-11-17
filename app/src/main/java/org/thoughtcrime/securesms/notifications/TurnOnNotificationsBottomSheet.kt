/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.R as CoreUiR

private const val PLACEHOLDER = "__TOGGLE_PLACEHOLDER__"

/**
 * Sheet explaining how to turn on notifications and providing an action to do so.
 */
class TurnOnNotificationsBottomSheet private constructor() : ComposeBottomSheetDialogFragment() {

  companion object {
    private const val ARG_TITLE = "argument.title_res"
    private const val ARG_SUBTITLE = "argument.subtitle_res"
    private const val ARG_STEP2 = "argument.step2_res"
    private const val ARG_SETTINGS_INTENT = "argument.settings_intent"

    @JvmStatic
    fun turnOnSystemNotificationsFragment(context: Context): ComposeBottomSheetDialogFragment {
      return TurnOnNotificationsBottomSheet().apply {
        arguments = bundleOf(
          ARG_TITLE to R.string.TurnOnNotificationsBottomSheet__turn_on_notifications,
          ARG_SUBTITLE to R.string.TurnOnNotificationsBottomSheet__to_receive_notifications,
          ARG_STEP2 to R.string.TurnOnNotificationsBottomSheet__2_s_turn_on_notifications,
          ARG_SETTINGS_INTENT to getNotificationsSettingsIntent(context)
        )
      }
    }

    @JvmStatic
    @RequiresApi(34)
    fun turnOnFullScreenIntentFragment(context: Context): ComposeBottomSheetDialogFragment {
      return TurnOnNotificationsBottomSheet().apply {
        arguments = bundleOf(
          ARG_TITLE to R.string.GrantFullScreenIntentPermission_bottomsheet_title,
          ARG_SUBTITLE to R.string.GrantFullScreenIntentPermission_bottomsheet_subtitle,
          ARG_STEP2 to R.string.GrantFullScreenIntentPermission_bottomsheet_step2,
          ARG_SETTINGS_INTENT to Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName))
        )
      }
    }

    private fun getNotificationsSettingsIntent(context: Context): Intent {
      return if (Build.VERSION.SDK_INT >= 26 && !NotificationChannels.getInstance().isMessageChannelEnabled) {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
          putExtra(Settings.EXTRA_CHANNEL_ID, NotificationChannels.getInstance().messagesChannel)
          putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
      } else if (Build.VERSION.SDK_INT >= 26 && (!NotificationChannels.getInstance().areNotificationsEnabled() || !NotificationChannels.getInstance().isMessagesChannelGroupEnabled)) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
          putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
      } else {
        AppSettingsActivity.notifications(context)
      }
    }
  }

  @Composable
  override fun SheetContent() {
    TurnOnNotificationsSheetContent(
      titleRes = requireArguments().getInt(ARG_TITLE),
      subtitleRes = requireArguments().getInt(ARG_SUBTITLE),
      step2Res = requireArguments().getInt(ARG_STEP2),
      onGoToSettingsClicked = this::goToSettings
    )
  }

  private fun goToSettings() {
    startActivity(BundleCompat.getParcelable(requireArguments(), ARG_SETTINGS_INTENT, Intent::class.java)!!)
    dismissAllowingStateLoss()
  }
}

@DayNightPreviews
@Composable
private fun TurnOnNotificationsSheetContentPreview() {
  Previews.Preview {
    Surface {
      TurnOnNotificationsSheetContent(
        titleRes = R.string.TurnOnNotificationsBottomSheet__turn_on_notifications,
        subtitleRes = R.string.TurnOnNotificationsBottomSheet__to_receive_notifications,
        step2Res = R.string.TurnOnNotificationsBottomSheet__2_s_turn_on_notifications
      ) {}
    }
  }
}

@Composable
private fun TurnOnNotificationsSheetContent(
  titleRes: Int,
  subtitleRes: Int,
  step2Res: Int,
  onGoToSettingsClicked: () -> Unit
) {
  Column(
    modifier = Modifier
      .padding(horizontal = dimensionResource(id = CoreUiR.dimen.gutter))
      .padding(bottom = 32.dp)
  ) {
    BottomSheets.Handle(
      modifier = Modifier.align(Alignment.CenterHorizontally)
    )

    Text(
      text = stringResource(titleRes),
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .padding(bottom = 12.dp, top = 10.dp)
    )

    Text(
      text = stringResource(subtitleRes),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .padding(bottom = 32.dp)
    )

    Text(
      text = stringResource(R.string.TurnOnNotificationsBottomSheet__1_tap_settings_below),
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(bottom = 32.dp)
    )

    val step2String = stringResource(id = step2Res, PLACEHOLDER)
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
      color = MaterialTheme.colorScheme.onSurface,
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
