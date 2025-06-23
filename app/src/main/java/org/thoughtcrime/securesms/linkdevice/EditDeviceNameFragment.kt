package org.thoughtcrime.securesms.linkdevice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment

/**
 * Fragment for changing the name of a linked device
 */
class EditDeviceNameFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(EditDeviceNameFragment::class)
    const val MAX_LENGTH = 50
  }

  private val viewModel: LinkDeviceViewModel by activityViewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController: NavController by remember { mutableStateOf(findNavController()) }
    val context = LocalContext.current

    LaunchedEffect(state.oneTimeEvent) {
      when (state.oneTimeEvent) {
        LinkDeviceSettingsState.OneTimeEvent.SnackbarNameChangeSuccess -> {
          Snackbar.make(requireView(), context.getString(R.string.EditDeviceNameFragment__device_name_updated), Snackbar.LENGTH_LONG).show()
          navController.popBackStack()
        }
        LinkDeviceSettingsState.OneTimeEvent.SnackbarNameChangeFailure -> {
          Snackbar.make(requireView(), context.getString(R.string.EditDeviceNameFragment__unable_to_change), Snackbar.LENGTH_LONG).show()
        }
        LinkDeviceSettingsState.OneTimeEvent.HideFinishedSheet,
        LinkDeviceSettingsState.OneTimeEvent.LaunchQrCodeScanner,
        LinkDeviceSettingsState.OneTimeEvent.None,
        LinkDeviceSettingsState.OneTimeEvent.ShowFinishedSheet,
        is LinkDeviceSettingsState.OneTimeEvent.ToastLinked,
        LinkDeviceSettingsState.OneTimeEvent.ToastNetworkFailed,
        is LinkDeviceSettingsState.OneTimeEvent.ToastUnlinked,
        LinkDeviceSettingsState.OneTimeEvent.LaunchEmail,
        LinkDeviceSettingsState.OneTimeEvent.SnackbarLinkCancelled -> Unit
      }
    }

    Scaffolds.Settings(
      title = stringResource(id = R.string.EditDeviceNameFragment__edit),
      onNavigationClick = { navController.popBackStack() },
      navigationIcon = ImageVector.vectorResource(id = R.drawable.symbol_arrow_start_24),
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close)
    ) { contentPadding: PaddingValues ->
      EditNameScreen(
        state = state,
        modifier = Modifier.padding(contentPadding),
        onSave = { viewModel.saveName(it) }
      )
    }
  }
}

@Composable
private fun EditNameScreen(
  state: LinkDeviceSettingsState,
  modifier: Modifier = Modifier,
  onSave: (String) -> Unit = {}
) {
  val focusRequester = remember { FocusRequester() }
  val name = state.deviceToEdit!!.name ?: ""
  var deviceName by remember { mutableStateOf(TextFieldValue(name, TextRange(name.length))) }

  Box(
    modifier = modifier.fillMaxHeight()
  ) {
    TextField(
      value = deviceName,
      label = { Text(text = stringResource(id = R.string.EditDeviceNameFragment__device_name)) },
      onValueChange = {
        deviceName = it.copy(
          text = it.text.substring(0, minOf(it.text.length, EditDeviceNameFragment.MAX_LENGTH))
        )
      },
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
      singleLine = true,
      colors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
      ),
      modifier = Modifier
        .fillMaxWidth()
        .focusRequester(focusRequester)
        .padding(top = 16.dp, bottom = 12.dp, start = 20.dp, end = 28.dp)
    )
    Buttons.MediumTonal(
      enabled = deviceName.text.isNotNullOrBlank() && (deviceName.text != name),
      onClick = { onSave(deviceName.text) },
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 24.dp, bottom = 16.dp)
    ) {
      Text(text = stringResource(R.string.EditDeviceNameFragment__save))
    }
  }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }
}

@SignalPreview
@Composable
private fun DeviceListScreenLinkingPreview() {
  Previews.Preview {
    EditNameScreen(
      state = LinkDeviceSettingsState(
        deviceToEdit = Device(1, "Laptop", 0, 0)
      )
    )
  }
}
