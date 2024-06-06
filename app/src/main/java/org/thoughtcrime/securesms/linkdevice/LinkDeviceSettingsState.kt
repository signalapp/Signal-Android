package org.thoughtcrime.securesms.linkdevice

import androidx.annotation.StringRes

/**
 * Information about linked devices. Used in [LinkDeviceViewModel].
 */
data class LinkDeviceSettingsState(
  val devices: List<Device> = emptyList(),
  val deviceToRemove: Device? = null,
  @StringRes val progressDialogMessage: Int = -1,
  val toastDialog: String = ""
)
