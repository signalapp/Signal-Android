package org.thoughtcrime.securesms.linkdevice

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.R

/**
 * Maintains the state of the [LinkDeviceFragment]
 */
class LinkDeviceViewModel : ViewModel() {

  private val _state = mutableStateOf(LinkDeviceSettingsState())
  val state: State<LinkDeviceSettingsState> = _state

  fun onResume() {
    _state.value = _state.value.copy()
  }

  fun setDeviceToRemove(device: Device?) {
    _state.value = _state.value.copy(deviceToRemove = device)
  }

  fun removeDevice(context: Context, device: Device) {
    viewModelScope.launch(Dispatchers.IO) {
      _state.value = _state.value.copy(
        progressDialogMessage = R.string.DeviceListActivity_unlinking_device
      )
      val success = LinkDeviceRepository.removeDevice(device.id)
      if (success) {
        loadDevices(context)
        _state.value = _state.value.copy(
          toastDialog = context.getString(R.string.LinkDeviceFragment__s_unlinked, device.name),
          progressDialogMessage = -1
        )
      } else {
        _state.value = _state.value.copy(
          progressDialogMessage = -1
        )
      }
    }
  }

  fun loadDevices(context: Context) {
    viewModelScope.launch(Dispatchers.IO) {
      val devices = LinkDeviceRepository.loadDevices()
      if (devices == null) {
        _state.value = _state.value.copy(
          toastDialog = context.getString(R.string.DeviceListActivity_network_failed),
          progressDialogMessage = -1
        )
      } else {
        _state.value = _state.value.copy(
          devices = devices,
          progressDialogMessage = -1
        )
      }
    }
  }
}
