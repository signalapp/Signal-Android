package org.thoughtcrime.securesms.linkdevice

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.LinkedDeviceInactiveCheckJob
import org.thoughtcrime.securesms.jobs.NewLinkedDeviceNotificationJob
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.linkdevice.LinkDeviceRepository.LinkDeviceResult
import org.thoughtcrime.securesms.linkdevice.LinkDeviceRepository.getPlaintextDeviceName
import org.thoughtcrime.securesms.linkdevice.LinkDeviceSettingsState.DialogState
import org.thoughtcrime.securesms.linkdevice.LinkDeviceSettingsState.OneTimeEvent
import org.thoughtcrime.securesms.linkdevice.LinkDeviceSettingsState.QrCodeState
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogRepository
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.Util
import org.whispersystems.signalservice.api.backup.MessageBackupKey
import org.whispersystems.signalservice.api.link.TransferArchiveError
import org.whispersystems.signalservice.api.link.WaitForLinkedDeviceResponse
import kotlin.jvm.optionals.getOrNull
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
  private val submitDebugLogRepository: SubmitDebugLogRepository = SubmitDebugLogRepository()

  private var pollJob: Job? = null

  fun initialize() {
    loadDevices(initialLoad = true)
    pollForDevices()
  }

  /**
   * Checks for the existence of a newly linked device and shows a dialog if it has since been unlinked
   */
  private fun checkForNewDevice(devices: List<Device>) {
    val newLinkedDeviceId = SignalStore.misc.newLinkedDeviceId
    val newLinkedDeviceCreatedAt = SignalStore.misc.newLinkedDeviceCreatedTime

    val hasNewLinkedDevice = newLinkedDeviceId > 0
    if (hasNewLinkedDevice) {
      ServiceUtil.getNotificationManager(AppDependencies.application).cancel(NotificationIds.NEW_LINKED_DEVICE)
      SignalStore.misc.newLinkedDeviceId = 0
      SignalStore.misc.newLinkedDeviceCreatedTime = 0
    }

    val isMissingNewLinkedDevice = devices.none { device -> device.id == newLinkedDeviceId && device.createdMillis == newLinkedDeviceCreatedAt }

    val dialogState = if (hasNewLinkedDevice && isMissingNewLinkedDevice) {
      DialogState.DeviceUnlinked(newLinkedDeviceCreatedAt)
    } else {
      DialogState.None
    }

    _state.update { it.copy(dialogState = dialogState) }
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

  private fun loadDevices(initialLoad: Boolean = false) {
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
        if (initialLoad) {
          checkForNewDevice(devices)
        }
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

  /**
   * Poll the server for devices every 5 seconds for 60 seconds
   */
  private fun pollForDevices() {
    stopExistingPolling()
    pollJob = viewModelScope.launch(Dispatchers.IO) {
      for (i in 1..12) {
        delay(5.seconds)
        val devices = LinkDeviceRepository.loadDevices()
        if (devices != null) {
          _state.value = _state.value.copy(
            devices = devices
          )
        }
      }
    }
  }

  fun stopExistingPolling() {
    pollJob?.cancel()
  }

  fun showFrontCamera() {
    _state.update {
      val frontCamera = it.showFrontCamera
      it.copy(
        showFrontCamera = if (frontCamera == null) true else !frontCamera
      )
    }
  }

  fun markQrEducationSheetSeen() {
    SignalStore.uiHints.markHasSeenLinkDeviceQrEducationSheet()
    _state.update {
      it.copy(
        seenQrEducationSheet = true,
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
          qrCodeState = if (uri.supportsLinkAndSync()) QrCodeState.VALID_WITH_SYNC else QrCodeState.VALID_WITHOUT_SYNC,
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

  fun onDialogDismissed() {
    _state.update { it.copy(dialogState = DialogState.None) }
  }

  fun addDevice(shouldSync: Boolean) = viewModelScope.launch(Dispatchers.IO) {
    val linkUri: Uri = _state.value.linkUri!!

    _state.update {
      it.copy(
        qrCodeState = QrCodeState.NONE,
        linkUri = null,
        dialogState = DialogState.Linking,
        shouldCancelArchiveUpload = false
      )
    }

    if (shouldSync) {
      Log.i(TAG, "Adding device with sync.")
      addDeviceWithSync(linkUri)
    } else {
      Log.i(TAG, "Adding device without sync. (uri: ${linkUri.supportsLinkAndSync()})")
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

  fun markBioAuthEducationSheetSeen(seen: Boolean) {
    SignalStore.uiHints.lastSeenLinkDeviceAuthSheetTime = System.currentTimeMillis()
    _state.update {
      it.copy(
        seenBioAuthEducationSheet = seen,
        needsBioAuthEducationSheet = false
      )
    }
  }

  private fun addDeviceWithSync(linkUri: Uri) {
    Log.d(TAG, "[addDeviceWithSync] Beginning device adding process.")

    val ephemeralMessageBackupKey = MessageBackupKey(Util.getSecretBytes(32))
    val result = LinkDeviceRepository.addDevice(linkUri, ephemeralMessageBackupKey)

    _state.update {
      it.copy(
        linkDeviceResult = result,
        qrCodeState = QrCodeState.NONE,
        linkUri = null
      )
    }

    Log.d(TAG, "[addDeviceWithSync] Got result: $result")

    if (result !is LinkDeviceResult.Success) {
      Log.w(TAG, "[addDeviceWithSync] Unable to link device $result", if (result is LinkDeviceResult.NetworkError) result.error else null)
      _state.update {
        it.copy(
          dialogState = DialogState.None
        )
      }
      return
    }

    Log.i(TAG, "[addDeviceWithSync] Waiting for a new linked device...")
    val waitResult: WaitForLinkedDeviceResponse? = LinkDeviceRepository.waitForDeviceToBeLinked(result.token, maxWaitTime = 60.seconds)
    if (waitResult == null) {
      Log.i(TAG, "[addDeviceWithSync] No linked device found!")
      _state.update {
        it.copy(
          dialogState = DialogState.SyncingTimedOut
        )
      }
      return
    }

    Log.d(TAG, "[addDeviceWithSync] Found a linked device! Creating notification job.")
    NewLinkedDeviceNotificationJob.enqueue(waitResult.id, waitResult.created)

    _state.update {
      it.copy(
        linkDeviceResult = result,
        dialogState = DialogState.SyncingMessages(waitResult.id, waitResult.created)
      )
    }

    Log.d(TAG, "[addDeviceWithSync] Beginning the archive generation process...")
    val uploadResult = LinkDeviceRepository.createAndUploadArchive(
      ephemeralMessageBackupKey = ephemeralMessageBackupKey,
      deviceId = waitResult.id,
      deviceCreatedAt = waitResult.created,
      cancellationSignal = { _state.value.shouldCancelArchiveUpload }
    )

    Log.d(TAG, "[addDeviceWithSync] Archive finished with result: $uploadResult")
    when (uploadResult) {
      LinkDeviceRepository.LinkUploadArchiveResult.Success -> {
        Log.i(TAG, "[addDeviceWithSync] Successfully uploaded archive.")
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
        Log.w(TAG, "[addDeviceWithSync] Failed to upload the archive! Result: $uploadResult")
        val canRetry = uploadResult !is LinkDeviceRepository.LinkUploadArchiveResult.BackupCreationFailure
        _state.update {
          it.copy(
            dialogState = DialogState.SyncingFailed(
              deviceId = waitResult.id,
              deviceCreatedAt = waitResult.created,
              canRetry = canRetry
            )
          )
        }
      }
      LinkDeviceRepository.LinkUploadArchiveResult.BackupCreationCancelled -> {
        Log.i(TAG, "[addDeviceWithoutSync] Cancelling archive upload")
        _state.update {
          it.copy(
            dialogState = DialogState.None
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
      Log.w(TAG, "Unable to link device $result", if (result is LinkDeviceResult.NetworkError) result.error else null)
      _state.update {
        it.copy(
          dialogState = DialogState.None
        )
      }
      return
    }

    Log.i(TAG, "Waiting for a new linked device...")
    val waitResult: WaitForLinkedDeviceResponse? = LinkDeviceRepository.waitForDeviceToBeLinked(result.token, maxWaitTime = 30.seconds)
    if (waitResult == null) {
      Log.i(TAG, "No linked device found!")
    } else {
      Log.i(TAG, "Found a linked device! Creating notification job.")
      NewLinkedDeviceNotificationJob.enqueue(waitResult.id, waitResult.created)
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
    return if (RemoteConfig.internalUser) {
      this.getQueryParameter("capabilities")?.split(",")?.contains("backup") == true ||
        this.getQueryParameter("capabilities")?.split(",")?.contains("backup2") == true ||
        this.getQueryParameter("capabilities")?.split(",")?.contains("backup3") == true
    } else {
      this.getQueryParameter("capabilities")?.split(",")?.contains("backup3") == true
    }
  }

  fun onSyncErrorIgnored() = viewModelScope.launch(Dispatchers.IO) {
    val dialogState = _state.value.dialogState
    if (dialogState is DialogState.SyncingFailed) {
      Log.i(TAG, "Alerting linked device of sync failure - will not retry")
      LinkDeviceRepository.sendTransferArchiveError(dialogState.deviceId, dialogState.deviceCreatedAt, TransferArchiveError.CONTINUE_WITHOUT_UPLOAD)
    }
    loadDevices()

    _state.update {
      it.copy(
        linkDeviceResult = LinkDeviceResult.None,
        dialogState = DialogState.None
      )
    }
  }

  fun onSyncErrorContactSupport() {
    _state.update {
      it.copy(
        dialogState = DialogState.ContactSupport
      )
    }
  }

  fun onSyncErrorRetryRequested() = viewModelScope.launch(Dispatchers.IO) {
    val dialogState = _state.value.dialogState
    if (dialogState is DialogState.SyncingFailed) {
      Log.i(TAG, "Alerting linked device of sync failure - will retry")
      LinkDeviceRepository.sendTransferArchiveError(dialogState.deviceId, dialogState.deviceCreatedAt, TransferArchiveError.RELINK_REQUESTED)

      Log.i(TAG, "Need to unlink device first...")
      val success = LinkDeviceRepository.removeDevice(dialogState.deviceId)
      if (!success) {
        Log.w(TAG, "Failed to remove device! We did our best. Continuing.")
      }
    }

    _state.update {
      it.copy(
        linkDeviceResult = LinkDeviceResult.None,
        dialogState = DialogState.None,
        oneTimeEvent = OneTimeEvent.LaunchQrCodeScanner
      )
    }
  }

  fun onSyncCancelled() = viewModelScope.launch(Dispatchers.IO) {
    Log.i(TAG, "Cancelling sync and removing linked device")
    val dialogState = _state.value.dialogState
    if (dialogState is DialogState.SyncingMessages) {
      val success = LinkDeviceRepository.removeDevice(dialogState.deviceId)
      if (success) {
        Log.i(TAG, "Removing device after cancelling sync")
        _state.update {
          it.copy(
            oneTimeEvent = OneTimeEvent.SnackbarLinkCancelled,
            dialogState = DialogState.None,
            shouldCancelArchiveUpload = true
          )
        }
      } else {
        Log.w(TAG, "Unable to remove device after cancelling sync")
      }
    }
  }

  fun setDeviceToEdit(device: Device) {
    stopExistingPolling()
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

  fun onContactSupport(includeLogs: Boolean) {
    viewModelScope.launch {
      if (includeLogs) {
        _state.update {
          it.copy(
            dialogState = DialogState.LoadingDebugLog
          )
        }
        submitDebugLogRepository.buildAndSubmitLog { result ->
          val url = result.getOrNull()
          _state.update {
            it.copy(
              debugLogUrl = url,
              oneTimeEvent = OneTimeEvent.LaunchEmail,
              dialogState = DialogState.None
            )
          }
        }
      } else {
        _state.update {
          it.copy(
            oneTimeEvent = OneTimeEvent.LaunchEmail,
            dialogState = DialogState.None
          )
        }
      }
    }
  }
}
