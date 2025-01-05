package org.thoughtcrime.securesms.linkdevice

import android.net.Uri
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
  val seenIntroSheet: Boolean = false,
  val seenEducationSheet: Boolean = false,
  val bottomSheetVisible: Boolean = false,
  val deviceToEdit: Device? = null
) {
  sealed interface DialogState {
    data object None : DialogState
    data object Linking : DialogState
    data object Unlinking : DialogState
    data object SyncingMessages : DialogState
    data object SyncingTimedOut : DialogState
    data class SyncingFailed(val deviceId: Int) : DialogState
  }

  sealed interface OneTimeEvent {
    data object None : OneTimeEvent
    data object ToastNetworkFailed : OneTimeEvent
    data class ToastUnlinked(val name: String) : OneTimeEvent
    data class ToastLinked(val name: String) : OneTimeEvent
    data object SnackbarNameChangeSuccess : OneTimeEvent
    data object SnackbarNameChangeFailure : OneTimeEvent
    data object ShowFinishedSheet : OneTimeEvent
    data object HideFinishedSheet : OneTimeEvent
    data object LaunchQrCodeScanner : OneTimeEvent
  }

  enum class QrCodeState {
    NONE, VALID_WITH_SYNC, VALID_WITHOUT_SYNC, INVALID
  }
}
