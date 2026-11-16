package org.signal.appsettings.usernamecolorpicker

import org.signal.appsettings.usernamelinks.QrCodeState
import org.signal.appsettings.usernamelinks.UsernameQrCodeColorScheme
import org.signal.core.util.censor

/**
 * [UsernameLinkQrColorPickerScreen] state.
 */
data class UsernameLinkQrColorPickerState(
  /** The user's username, shown under the QR code. */
  val username: String = "",
  /** The QR code for the user's username link. */
  val qrCodeData: QrCodeState = QrCodeState.Loading,
  /** The color the QR code is shown and shared in. */
  val selectedColorScheme: UsernameQrCodeColorScheme = UsernameQrCodeColorScheme.Blue
) {
  override fun toString(): String = "UsernameLinkQrColorPickerState(username=${username.censor()}, qrCodeData=${qrCodeData.javaClass.simpleName}, selectedColorScheme=$selectedColorScheme)"
}
