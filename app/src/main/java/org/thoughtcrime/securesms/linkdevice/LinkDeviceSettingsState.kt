package org.thoughtcrime.securesms.linkdevice

import android.net.Uri
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.linkdevice.LinkDeviceRepository.LinkDeviceResult

/**
 * Information about linked devices. Used in [LinkDeviceViewModel].
 */
data class LinkDeviceSettingsState(
  val devices: List<Device> = emptyList(),
  val deviceToRemove: Device? = null,
  val dialogState: DialogState = DialogState.None,
  val deviceListLoading: Boolean = false,
  val oneTimeEvent: OneTimeEvent = OneTimeEvent.None,
  val showFrontCamera: Boolean? = null,
  val qrCodeState: QrCodeState = QrCodeState.NONE,
  val linkUri: Uri? = null,
  val linkDeviceResult: LinkDeviceResult = LinkDeviceResult.None,
  val seenQrEducationSheet: Boolean = SignalStore.uiHints.hasSeenLinkDeviceQrEducationSheet() || SignalStore.account.isMultiDevice,
  val bottomSheetVisible: Boolean = false,
  val deviceToEdit: Device? = null,
  val shouldCancelArchiveUpload: Boolean = false,
  val debugLogUrl: String? = null
) {
  sealed interface DialogState {
    data object None : DialogState
    data object Linking : DialogState
    data object Unlinking : DialogState
    data class SyncingMessages(val deviceId: Int) : DialogState
    data object SyncingTimedOut : DialogState
    data class SyncingFailed(val deviceId: Int, val deviceRegistrationId: Int, val syncFailType: SyncFailType) : DialogState
    data class DeviceUnlinked(val deviceCreatedAt: Long) : DialogState
    data object LoadingDebugLog : DialogState
    data object ContactSupport : DialogState
  }

  sealed interface OneTimeEvent {
    data object None : OneTimeEvent
    data object ToastNetworkFailed : OneTimeEvent
    data class ToastUnlinked(val name: String) : OneTimeEvent
    data class ToastLinked(val name: String) : OneTimeEvent
    data object SnackbarLinkCancelled : OneTimeEvent
    data object SnackbarNameChangeSuccess : OneTimeEvent
    data object SnackbarNameChangeFailure : OneTimeEvent
    data object ShowFinishedSheet : OneTimeEvent
    data object HideFinishedSheet : OneTimeEvent
    data object LaunchQrCodeScanner : OneTimeEvent
    data object LaunchEmail : OneTimeEvent
  }

  enum class QrCodeState {
    NONE, VALID_WITH_SYNC, VALID_WITHOUT_SYNC, INVALID
  }

  enum class SyncFailType {
    NOT_RETRYABLE,
    RETRYABLE,
    NOT_ENOUGH_SPACE
  }
}
