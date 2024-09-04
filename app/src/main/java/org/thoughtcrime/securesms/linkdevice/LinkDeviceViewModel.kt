package org.thoughtcrime.securesms.linkdevice

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobTracker
import org.thoughtcrime.securesms.jobs.LinkedDeviceInactiveCheckJob
import org.thoughtcrime.securesms.jobs.MultiDeviceConfigurationUpdateJob

/**
 * Maintains the state of the [LinkDeviceFragment]
 */
class LinkDeviceViewModel : ViewModel() {

  private val _state = MutableStateFlow(LinkDeviceSettingsState())
  val state = _state.asStateFlow()

  private lateinit var listener: JobTracker.JobListener

  fun initialize(context: Context) {
    listener = JobTracker.JobListener { _, jobState ->
      if (jobState.isComplete) {
        loadDevices(context = context, isPotentialNewDevice = true)
      }
    }
    AppDependencies.jobManager.addListener(
      { job: Job -> job.parameters.queue?.startsWith(MultiDeviceConfigurationUpdateJob.QUEUE) ?: false },
      listener
    )
    loadDevices(context)
  }

  override fun onCleared() {
    super.onCleared()
    AppDependencies.jobManager.removeListener(listener)
  }

  fun setDeviceToRemove(device: Device?) {
    _state.update { it.copy(deviceToRemove = device) }
  }

  fun removeDevice(context: Context, device: Device) {
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(progressDialogMessage = R.string.DeviceListActivity_unlinking_device) }

      val success = LinkDeviceRepository.removeDevice(device.id)
      if (success) {
        loadDevices(context)
        _state.value = _state.value.copy(
          toastDialog = context.getString(R.string.LinkDeviceFragment__s_unlinked, device.name),
          progressDialogMessage = -1
        )
      } else {
        _state.update {
          it.copy(progressDialogMessage = -1)
        }
      }
    }
  }

  private fun loadDevices(context: Context, isPotentialNewDevice: Boolean = false) {
    if (isPotentialNewDevice && !_state.value.pendingNewDevice) {
      return
    }
    _state.value = _state.value.copy(
      progressDialogMessage = if (isPotentialNewDevice) R.string.LinkDeviceFragment__linking_device else R.string.LinkDeviceFragment__loading,
      pendingNewDevice = if (isPotentialNewDevice) false else _state.value.pendingNewDevice,
      showFrontCamera = null
    )
    viewModelScope.launch(Dispatchers.IO) {
      val devices = LinkDeviceRepository.loadDevices()
      if (devices == null) {
        _state.value = _state.value.copy(
          toastDialog = context.getString(R.string.DeviceListActivity_network_failed),
          progressDialogMessage = -1
        )
      } else {
        _state.update {
          it.copy(
            toastDialog = if (isPotentialNewDevice) context.getString(R.string.LinkDeviceFragment__device_approved) else "",
            devices = devices,
            progressDialogMessage = -1
          )
        }
      }
    }
  }

  fun showFrontCamera() {
    _state.update {
      val frontCamera = it.showFrontCamera
      it.copy(
        showFrontCamera = if (frontCamera == null) true else !frontCamera
      )
    }
  }

  fun markIntroSheetSeen() {
    _state.update {
      it.copy(
        seenIntroSheet = true,
        showFrontCamera = null
      )
    }
  }

  fun onQrCodeScanned(url: String) {
    if (_state.value.qrCodeFound || _state.value.qrCodeInvalid) {
      return
    }

    val uri = Uri.parse(url)
    if (LinkDeviceRepository.isValidQr(uri)) {
      _state.update {
        it.copy(
          qrCodeFound = true,
          qrCodeInvalid = false,
          url = url,
          showFrontCamera = null
        )
      }
    } else {
      _state.update {
        it.copy(
          qrCodeFound = false,
          qrCodeInvalid = true,
          url = url,
          showFrontCamera = null
        )
      }
    }
  }

  fun onQrCodeDismissed() {
    _state.update {
      it.copy(
        qrCodeFound = false,
        qrCodeInvalid = false
      )
    }
  }

  fun addDevice() {
    val uri = Uri.parse(_state.value.url)
    viewModelScope.launch(Dispatchers.IO) {
      val result = LinkDeviceRepository.addDevice(uri)
      _state.update {
        it.copy(
          qrCodeFound = false,
          qrCodeInvalid = false,
          linkDeviceResult = result,
          url = ""
        )
      }
      LinkedDeviceInactiveCheckJob.enqueue()
    }
  }

  fun onLinkDeviceResult(showSheet: Boolean) {
    _state.update {
      it.copy(
        showFinishedSheet = showSheet,
        linkDeviceResult = LinkDeviceRepository.LinkDeviceResult.UNKNOWN,
        toastDialog = "",
        pendingNewDevice = true
      )
    }
  }

  fun markFinishedSheetSeen() {
    _state.update {
      it.copy(
        showFinishedSheet = false
      )
    }
  }

  fun clearToast() {
    _state.update {
      it.copy(
        toastDialog = ""
      )
    }
  }

  fun markEducationSheetSeen(seen: Boolean) {
    _state.update {
      it.copy(
        seenEducationSheet = seen
      )
    }
  }
}
