package org.thoughtcrime.securesms.components.settings.app.usernamelinks.colorpicker

import kotlinx.collections.immutable.ImmutableList
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeData
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.UsernameQrCodeColorScheme

data class UsernameLinkQrColorPickerState(
  val username: String,
  val qrCodeData: QrCodeData?,
  val colorSchemes: ImmutableList<UsernameQrCodeColorScheme>,
  val selectedColorScheme: UsernameQrCodeColorScheme
)
