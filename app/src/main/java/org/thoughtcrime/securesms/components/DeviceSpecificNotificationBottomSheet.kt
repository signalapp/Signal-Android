package org.thoughtcrime.securesms.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.Previews
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.notifications.DeviceSpecificNotificationConfig
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.CommunicationActions

class DeviceSpecificNotificationBottomSheet : ComposeBottomSheetDialogFragment() {

  override val peekHeightPercentage: Float = 0.66f

  companion object {
    private const val ARG_LINK = "arg.link"
    private const val ARG_LINK_VERSION = "arg.link.version"

    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      if (fragmentManager.findFragmentByTag(BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG) == null) {
        val dialog = DeviceSpecificNotificationBottomSheet().apply {
          arguments = bundleOf(
            ARG_LINK to DeviceSpecificNotificationConfig.currentConfig.link,
            ARG_LINK_VERSION to DeviceSpecificNotificationConfig.currentConfig.version
          )
        }
        BottomSheetUtil.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG, dialog)
        SignalStore.uiHints.lastSupportVersionSeen = DeviceSpecificNotificationConfig.currentConfig.version
      }
    }
  }

  @Composable
  override fun SheetContent() {
    DeviceSpecificSheet(this::onContinue, this::dismissAllowingStateLoss)
  }

  private fun onContinue() {
    val link = arguments?.getString(ARG_LINK) ?: getString(R.string.PromptBatterySaverBottomSheet__learn_more_url)
    CommunicationActions.openBrowserLink(requireContext(), link)
  }
}

@Composable
private fun DeviceSpecificSheet(onContinue: () -> Unit = {}, onDismiss: () -> Unit = {}) {
  return Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.Center)
  ) {
    BottomSheets.Handle()
    Icon(
      painterResource(id = R.drawable.ic_troubleshoot_notification),
      contentDescription = null,
      tint = Color.Unspecified,
      modifier = Modifier.padding(top = 32.dp, bottom = 8.dp)
    )
    Text(
      text = stringResource(id = R.string.DeviceSpecificNotificationBottomSheet__notifications_may_be_delayed),
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
    Text(
      text = stringResource(id = R.string.DeviceSpecificNotificationBottomSheet__disable_battery_optimizations),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 24.dp)
    )
    Row(
      modifier = Modifier.padding(top = 60.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
    ) {
      Buttons.MediumTonal(
        onClick = onDismiss,
        modifier = Modifier.padding(end = 12.dp).weight(1f)
      ) {
        Text(stringResource(id = R.string.DeviceSpecificNotificationBottomSheet__no_thanks))
      }
      Buttons.MediumTonal(
        onClick = onContinue,
        modifier = Modifier.padding(start = 12.dp).weight(1f)
      ) {
        Icon(painterResource(id = R.drawable.ic_open_20), contentDescription = null, modifier = Modifier.padding(end = 4.dp))
        Text(stringResource(id = R.string.DeviceSpecificNotificationBottomSheet__continue))
      }
    }
  }
}

@SignalPreview
@Composable
private fun DeviceSpecificSheetPreview() {
  Previews.BottomSheetPreview {
    DeviceSpecificSheet()
  }
}
