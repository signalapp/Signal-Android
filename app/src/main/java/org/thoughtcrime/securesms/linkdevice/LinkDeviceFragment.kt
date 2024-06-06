package org.thoughtcrime.securesms.linkdevice

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Dividers
import org.signal.core.ui.Previews
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.SignalPreview
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import java.util.Locale

/**
 * Fragment that shows current linked devices
 */
class LinkDeviceFragment : ComposeFragment() {

  private val viewModel: LinkDeviceViewModel by activityViewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.toastDialog) {
      if (state.toastDialog.isNotEmpty()) {
        Toast.makeText(requireContext(), state.toastDialog, Toast.LENGTH_LONG).show()
      }
    }

    LaunchedEffect(state.showFinishedSheet) {
      if (state.showFinishedSheet) {
        onShowFinishedSheet()
      }
    }

    Scaffolds.Settings(
      title = stringResource(id = R.string.preferences__linked_devices),
      onNavigationClick = { findNavController().popBackStack() },
      navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24),
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close)
    ) { contentPadding: PaddingValues ->
      DeviceDescriptionScreen(
        state = state,
        modifier = Modifier.padding(contentPadding),
        onLearnMore = this::openLearnMore,
        onLinkDevice = this::openLinkNewDevice,
        setDeviceToRemove = this::setDeviceToRemove,
        onRemoveDevice = this::onRemoveDevice
      )
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.loadDevices(requireContext())
  }

  private fun openLearnMore() {
    LinkDeviceLearnMoreBottomSheetFragment().show(childFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
  }

  private fun openLinkNewDevice() {
    findNavController().safeNavigate(R.id.action_linkDeviceFragment_to_addLinkDeviceFragment)
  }

  private fun setDeviceToRemove(device: Device?) {
    viewModel.setDeviceToRemove(device)
  }

  private fun onRemoveDevice(device: Device) {
    viewModel.removeDevice(requireContext(), device)
  }

  private fun onShowFinishedSheet() {
    LinkDeviceFinishedSheet().show(childFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    viewModel.markFinishedSheetSeen()
  }
}

@Composable
fun DeviceDescriptionScreen(
  state: LinkDeviceSettingsState,
  modifier: Modifier = Modifier,
  onLearnMore: () -> Unit = {},
  onLinkDevice: () -> Unit = {},
  setDeviceToRemove: (Device?) -> Unit = {},
  onRemoveDevice: (Device) -> Unit = {}
) {
  if (state.progressDialogMessage != -1) {
    Dialogs.IndeterminateProgressDialog(stringResource(id = state.progressDialogMessage))
  }
  if (state.deviceToRemove != null) {
    val device: Device = state.deviceToRemove
    Dialogs.SimpleAlertDialog(
      title = stringResource(id = R.string.DeviceListActivity_unlink_s, device.name),
      body = stringResource(id = R.string.DeviceListActivity_by_unlinking_this_device_it_will_no_longer_be_able_to_send_or_receive),
      confirm = stringResource(R.string.LinkDeviceFragment__unlink),
      dismiss = stringResource(android.R.string.cancel),
      onConfirm = { onRemoveDevice(device) },
      onDismiss = { setDeviceToRemove(null) }
    )
  }

  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.verticalScroll(rememberScrollState())) {
    Icon(
      painter = painterResource(R.drawable.ic_devices_intro),
      contentDescription = stringResource(R.string.preferences__linked_devices),
      tint = Color.Unspecified
    )
    Text(
      text = stringResource(id = R.string.LinkDeviceFragment__use_signal_on_desktop_ipad),
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)
    )
    ClickableText(
      text = AnnotatedString(stringResource(id = R.string.LearnMoreTextView_learn_more)),
      style = TextStyle(color = MaterialTheme.colorScheme.primary)
    ) {
      onLearnMore()
    }

    Spacer(modifier = Modifier.size(20.dp))

    Buttons.LargeTonal(
      onClick = onLinkDevice,
      modifier = Modifier.width(300.dp)
    ) {
      Text(stringResource(id = R.string.LinkDeviceFragment__link_a_new_device))
    }

    if (state.devices.isNotEmpty()) {
      Dividers.Default()

      Column {
        Text(
          text = stringResource(R.string.LinkDeviceFragment__my_linked_devices),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 24.dp)
        )
        state.devices.forEach { device ->
          DeviceRow(device, setDeviceToRemove)
        }
      }
    }

    Row(
      modifier = Modifier.padding(horizontal = 40.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Spacer(modifier = Modifier.size(12.dp))
      Icon(
        painter = painterResource(R.drawable.symbol_lock_24),
        contentDescription = null
      )
      Text(
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        text = stringResource(id = R.string.LinkDeviceFragment__messages_and_chat_info_are_protected)
      )
    }
  }
}

@Composable
fun DeviceRow(device: Device, setDeviceToRemove: (Device) -> Unit) {
  val titleString = device.name.ifEmpty { stringResource(R.string.DeviceListItem_unnamed_device) }
  val linkedDate = DateUtils.getDayPrecisionTimeSpanString(LocalContext.current, Locale.getDefault(), device.createdMillis)
  val lastActive = DateUtils.getDayPrecisionTimeSpanString(LocalContext.current, Locale.getDefault(), device.lastSeenMillis)

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { setDeviceToRemove(device) },
    verticalAlignment = Alignment.CenterVertically
  ) {
    Image(
      painter = painterResource(id = R.drawable.symbol_devices_24),
      contentDescription = null,
      colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
      contentScale = ContentScale.Inside,
      modifier = Modifier
        .padding(start = 24.dp)
        .size(40.dp)
        .background(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = CircleShape
        )
    )
    Spacer(modifier = Modifier.size(20.dp))
    Column {
      Text(text = titleString, style = MaterialTheme.typography.bodyLarge)
      Spacer(modifier = Modifier.size(4.dp))
      Text(stringResource(R.string.DeviceListItem_linked_s, linkedDate), style = MaterialTheme.typography.bodyMedium)
      Text(stringResource(R.string.DeviceListItem_last_active_s, lastActive), style = MaterialTheme.typography.bodyMedium)
    }
  }
  Spacer(modifier = Modifier.size(16.dp))
}

@SignalPreview
@Composable
private fun DeviceScreenPreview() {
  val previewDevices = listOf(
    Device(1, "Sam's Macbook Pro", 1715793982000, 1716053182000),
    Device(1, "Sam's iPad", 1715793182000, 1716053122000)
  )
  val previewState = LinkDeviceSettingsState(devices = previewDevices)

  Previews.Preview {
    DeviceDescriptionScreen(previewState)
  }
}
