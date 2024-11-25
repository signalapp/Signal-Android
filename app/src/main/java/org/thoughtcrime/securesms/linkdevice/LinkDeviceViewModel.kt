package org.thoughtcrime.securesms.linkdevice

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.jobs.LinkedDeviceInactiveCheckJob
import org.thoughtcrime.securesms.linkdevice.LinkDeviceRepository.LinkDeviceResult
import org.thoughtcrime.securesms.linkdevice.LinkDeviceRepository.getPlaintextDeviceName
import org.thoughtcrime.securesms.linkdevice.LinkDeviceSettingsState.DialogState
import org.thoughtcrime.securesms.linkdevice.LinkDeviceSettingsState.OneTimeEvent
import org.thoughtcrime.securesms.linkdevice.LinkDeviceSettingsState.QrCodeState
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.backup.MessageBackupKey
import org.whispersystems.signalservice.api.link.WaitForLinkedDeviceResponse
import kotlin.time.Duration.Companion.seconds

/**
 * Maintains the state of the [LinkDeviceFragment]
 */
class LinkDeviceViewModel : ViewModel() {

  companion object {
    val TAG = Log.tag(LinkDeviceViewModel::class)
  }

  private val _state = MutableStateFlow(LinkDeviceSettingsState())
  val state = _state.asStateFlow()

  fun initialize() {
    loadDevices()
  }

  fun setDeviceToRemove(device: Device?) {
    _state.update { it.copy(deviceToRemove = device) }
  }

  fun removeDevice(device: Device) {
    viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(dialogState = DialogState.Unlinking) }

      val success = LinkDeviceRepository.removeDevice(device.id)
      if (success) {
        loadDevices()
        _state.value = _state.value.copy(
          oneTimeEvent = OneTimeEvent.ToastUnlinked(device.name ?: ""),
          dialogState = DialogState.None,
          deviceToRemove = null
        )
      } else {
        _state.update {
          it.copy(
            dialogState = DialogState.None,
            deviceToRemove = null
          )
        }
      }
    }
  }

  private fun loadDevices() {
    _state.value = _state.value.copy(
      deviceListLoading = true,
      showFrontCamera = null
    )

    viewModelScope.launch(Dispatchers.IO) {
      val devices = LinkDeviceRepository.loadDevices()
      if (devices == null) {
        _state.value = _state.value.copy(
          oneTimeEvent = OneTimeEvent.ToastNetworkFailed,
          deviceListLoading = false
        )
      } else {
        _state.update {
          it.copy(
            oneTimeEvent = OneTimeEvent.None,
            devices = devices,
            deviceListLoading = false
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
    if (_state.value.qrCodeState != QrCodeState.NONE) {
      return
    }

    val uri = Uri.parse(url)
    if (LinkDeviceRepository.isValidQr(uri)) {
      _state.update {
        it.copy(
          qrCodeState = if (uri.supportsLinkAndSync() && RemoteConfig.linkAndSync) QrCodeState.VALID_WITH_SYNC else QrCodeState.VALID_WITHOUT_SYNC,
          linkUri = uri,
          showFrontCamera = null
        )
      }
    } else {
      _state.update {
        it.copy(
          qrCodeState = QrCodeState.INVALID,
          linkUri = uri,
          showFrontCamera = null
        )
      }
    }
  }

  fun onQrCodeDismissed() {
    _state.update {
      it.copy(
        qrCodeState = QrCodeState.NONE
      )
    }
  }

  fun addDevice(shouldSync: Boolean) = viewModelScope.launch(Dispatchers.IO) {
    val linkUri: Uri = _state.value.linkUri!!

    _state.update {
      it.copy(
        qrCodeState = QrCodeState.NONE,
        linkUri = null,
        dialogState = DialogState.Linking
      )
    }

    if (shouldSync) {
      Log.i(TAG, "Adding device with sync.")
      addDeviceWithSync(linkUri)
    } else {
      Log.i(TAG, "Adding device without sync. (uri: ${linkUri.supportsLinkAndSync()}, remoteConfig: ${RemoteConfig.linkAndSync})")
      addDeviceWithoutSync(linkUri)
    }
  }

  fun onLinkDeviceResult(showSheet: Boolean) {
    _state.update {
      it.copy(
        linkDeviceResult = LinkDeviceResult.None,
        oneTimeEvent = if (showSheet) {
          OneTimeEvent.ShowFinishedSheet
        } else {
          OneTimeEvent.None
        }
      )
    }
  }

  fun onBottomSheetVisible() {
    _state.update {
      it.copy(bottomSheetVisible = true)
    }
  }

  fun onBottomSheetDismissed() {
    _state.update {
      it.copy(bottomSheetVisible = false)
    }
  }

  fun clearOneTimeEvent() {
    _state.update {
      it.copy(oneTimeEvent = OneTimeEvent.None)
    }
  }

  fun markEducationSheetSeen(seen: Boolean) {
    _state.update {
      it.copy(seenEducationSheet = seen)
    }
  }

  private fun addDeviceWithSync(linkUri: Uri) {
    val ephemeralMessageBackupKey = MessageBackupKey(Util.getSecretBytes(32))
    val result = LinkDeviceRepository.addDevice(linkUri, ephemeralMessageBackupKey)

    _state.update {
      it.copy(
        linkDeviceResult = result,
        qrCodeState = QrCodeState.NONE,
        linkUri = null
      )
    }

    if (result !is LinkDeviceResult.Success) {
      Log.w(TAG, "Unable to link device $result")
      return
    }

    Log.i(TAG, "Waiting for a new linked device...")
    val waitResult: WaitForLinkedDeviceResponse? = LinkDeviceRepository.waitForDeviceToBeLinked(result.token, maxWaitTime = 60.seconds)
    if (waitResult == null) {
      Log.i(TAG, "No linked device found!")
      _state.update {
        it.copy(
          dialogState = DialogState.SyncingTimedOut
        )
      }
      return
    }

    Log.i(TAG, "Found a linked device!")

    _state.update {
      it.copy(
        linkDeviceResult = result,
        dialogState = DialogState.SyncingMessages
      )
    }

    Log.i(TAG, "Beginning the archive generation process...")
    val uploadResult = LinkDeviceRepository.createAndUploadArchive(ephemeralMessageBackupKey, waitResult.id, waitResult.created)
    when (uploadResult) {
      LinkDeviceRepository.LinkUploadArchiveResult.Success -> {
        _state.update {
          it.copy(
            oneTimeEvent = OneTimeEvent.ToastLinked(waitResult.getPlaintextDeviceName()),
            dialogState = DialogState.None
          )
        }
        loadDevices()
      }
      is LinkDeviceRepository.LinkUploadArchiveResult.BackupCreationFailure,
      is LinkDeviceRepository.LinkUploadArchiveResult.BadRequest,
      is LinkDeviceRepository.LinkUploadArchiveResult.NetworkError -> {
        _state.update {
          it.copy(
            dialogState = DialogState.SyncingFailed(waitResult.id)
          )
        }
      }
    }
  }

  private fun addDeviceWithoutSync(linkUri: Uri) {
    val result = LinkDeviceRepository.addDevice(linkUri, ephemeralMessageBackupKey = null)

    _state.update {
      it.copy(
        linkDeviceResult = result,
        qrCodeState = QrCodeState.NONE,
        linkUri = null
      )
    }

    if (result !is LinkDeviceResult.Success) {
      Log.w(TAG, "Unable to link device $result")
      return
    }

    Log.i(TAG, "Waiting for a new linked device...")
    val waitResult: WaitForLinkedDeviceResponse? = LinkDeviceRepository.waitForDeviceToBeLinked(result.token, maxWaitTime = 30.seconds)
    if (waitResult == null) {
      Log.i(TAG, "No linked device found!")
    } else {
      _state.update {
        it.copy(oneTimeEvent = OneTimeEvent.ToastLinked(waitResult.getPlaintextDeviceName()))
      }
    }

    _state.update {
      it.copy(
        linkDeviceResult = LinkDeviceResult.None,
        dialogState = DialogState.None
      )
    }

    loadDevices()

    LinkedDeviceInactiveCheckJob.enqueue()
  }

  private fun Uri.supportsLinkAndSync(): Boolean {
    return this.getQueryParameter("capabilities")?.split(",")?.contains("backup") == true
  }

  fun onSyncErrorIgnored() {
    _state.update {
      it.copy(dialogState = DialogState.None)
    }
  }

  fun onSyncErrorRetryRequested(deviceId: Int?) = viewModelScope.launch(Dispatchers.IO) {
    if (deviceId != null) {
      Log.i(TAG, "Need to unlink device first...")
      val success = LinkDeviceRepository.removeDevice(deviceId)
      if (!success) {
        Log.w(TAG, "Failed to remove device! We did our best. Continuing.")
      }
    }

    _state.update {
      it.copy(
        dialogState = DialogState.None,
        oneTimeEvent = OneTimeEvent.LaunchQrCodeScanner
      )
    }
  }

  fun setDeviceToEdit(device: Device) {
    _state.update {
      it.copy(
        deviceToEdit = device
      )
    }
  }

  fun saveName(name: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val device = _state.value.deviceToEdit!!
      val result = LinkDeviceRepository.changeDeviceName(name, device.id)
      val event = when (result) {
        LinkDeviceRepository.DeviceNameChangeResult.Success -> OneTimeEvent.SnackbarNameChangeSuccess
        is LinkDeviceRepository.DeviceNameChangeResult.NetworkError -> OneTimeEvent.SnackbarNameChangeFailure
      }

      _state.update {
        it.copy(
          oneTimeEvent = event
        )
      }
    }
  }
}
