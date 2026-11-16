package org.thoughtcrime.securesms.linkdevice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.snackbar.Snackbar
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.navigateBack
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.util.isNotNullOrBlank
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter
import org.signal.core.ui.R as CoreUiR

/**
 * Fragment for changing the name of a linked device
 */
class EditDeviceNameFragment : ComposeFragment() {

  companion object {
    const val MAX_LENGTH = 50
  }

  private val appSettingsRouter by appSettingsRouter()

  @Composable
  override fun FragmentContent() {
    EditDeviceNameEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }
}

/**
 * Renames the linked device picked from the device list, leaving through [parentEventEmitter] once it saves.
 */
@Composable
fun EditDeviceNameEntry(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  val view = LocalView.current
  val viewModel = linkDeviceViewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val nameUpdated = stringResource(R.string.EditDeviceNameFragment__device_name_updated)
  val unableToChange = stringResource(R.string.EditDeviceNameFragment__unable_to_change)

  LaunchedEffect(state.oneTimeEvent) {
    when (state.oneTimeEvent) {
      LinkDeviceSettingsState.OneTimeEvent.SnackbarNameChangeSuccess -> {
        Snackbar.make(view, nameUpdated, Snackbar.LENGTH_LONG).show()
        parentEventEmitter.navigateBack()
      }
      LinkDeviceSettingsState.OneTimeEvent.SnackbarNameChangeFailure -> {
        Snackbar.make(view, unableToChange, Snackbar.LENGTH_LONG).show()
      }
      LinkDeviceSettingsState.OneTimeEvent.HideFinishedSheet,
      LinkDeviceSettingsState.OneTimeEvent.LaunchQrCodeScanner,
      LinkDeviceSettingsState.OneTimeEvent.None,
      LinkDeviceSettingsState.OneTimeEvent.ShowFinishedSheet,
      is LinkDeviceSettingsState.OneTimeEvent.ToastLinked,
      LinkDeviceSettingsState.OneTimeEvent.ToastNetworkFailed,
      is LinkDeviceSettingsState.OneTimeEvent.ToastUnlinked,
      LinkDeviceSettingsState.OneTimeEvent.LaunchEmail,
      LinkDeviceSettingsState.OneTimeEvent.SnackbarDeviceLimitReached,
      LinkDeviceSettingsState.OneTimeEvent.SnackbarLinkCancelled -> Unit
    }
  }

  Scaffolds.Settings(
    title = stringResource(id = R.string.EditDeviceNameFragment__edit),
    onNavigationClick = { parentEventEmitter.navigateBack() },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(id = CoreUiR.string.CloseButton__content_description)
  ) { contentPadding: PaddingValues ->
    EditNameScreen(
      state = state,
      modifier = Modifier
        .padding(contentPadding)
        .consumeWindowInsets(contentPadding)
        .imePadding(),
      onSave = { viewModel.saveName(it) }
    )
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

@DayNightPreviews
@Composable
private fun DeviceListScreenLinkingPreview() {
  Previews.Preview {
    EditNameScreen(
      state = LinkDeviceSettingsState(
        deviceToEdit = Device(1, "Laptop", 0, 0, 0)
      )
    )
  }
}
