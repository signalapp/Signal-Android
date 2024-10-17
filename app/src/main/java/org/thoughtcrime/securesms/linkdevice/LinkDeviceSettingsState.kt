package org.thoughtcrime.securesms.linkdevice

import androidx.annotation.StringRes

/**
 * Information about linked devices. Used in [LinkDeviceViewModel].
 */
data class LinkDeviceSettingsState(
  val devices: List<Device> = emptyList(),
  val deviceToRemove: Device? = null,
  @StringRes val progressDialogMessage: Int = -1,
  val toastDialog: String = "",
  val showFrontCamera: Boolean? = null,
  val qrCodeFound: Boolean = false,
  val qrCodeInvalid: Boolean = false,
  val url: String = "",
  val linkDeviceResult: LinkDeviceRepository.LinkDeviceResult = LinkDeviceRepository.LinkDeviceResult.UNKNOWN,
  val showFinishedSheet: Boolean = false,
  val seenIntroSheet: Boolean = false,
  val pendingNewDevice: Boolean = false,
  val seenEducationSheet: Boolean = false
)
