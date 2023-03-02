package org.thoughtcrime.securesms.conversationlist

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import org.signal.core.ui.Buttons
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.BottomSheetUtil

/**
 * Bottom Sheet Dialog to remind a user who has just re-registered to re-link their linked devices.
 */
class RelinkDevicesReminderBottomSheetFragment : ComposeBottomSheetDialogFragment() {

  @Preview
  @Composable
  override fun SheetContent() {
    return Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .padding(16.dp)
        .wrapContentSize()
    ) {
      Handle()
      Column(horizontalAlignment = Alignment.Start) {
        Text(
          text = stringResource(id = R.string.RelinkDevicesReminderFragment__relink_your_devices),
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier
            .padding(8.dp)
        )
        Text(
          text = stringResource(R.string.RelinkDevicesReminderFragment__the_devices_you_added_were_unlinked),
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(8.dp)
        )
      }
      Buttons.LargeTonal(
        onClick = ::launchLinkedDevicesSettingsPage,
        modifier = Modifier
          .padding(8.dp)
          .fillMaxWidth()
          .align(Alignment.Start)
      ) {
        Text(
          text = stringResource(R.string.RelinkDevicesReminderFragment__open_settings),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onPrimaryContainer
        )
      }
      TextButton(
        onClick = ::dismiss,
        modifier = Modifier
          .padding(start = 8.dp, end = 8.dp)
          .wrapContentSize()
      ) {
        Text(
          text = stringResource(R.string.RelinkDevicesReminderFragment__later),
          color = MaterialTheme.colorScheme.primary
        )
      }
    }
  }

  @SuppressLint("DiscouragedApi")
  private fun launchLinkedDevicesSettingsPage() {
    startActivity(AppSettingsActivity.linkedDevices(requireContext()))
  }

  companion object {
    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      RelinkDevicesReminderBottomSheetFragment().show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }
}
