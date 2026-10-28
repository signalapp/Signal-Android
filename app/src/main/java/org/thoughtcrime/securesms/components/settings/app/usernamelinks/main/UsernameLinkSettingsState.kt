package org.thoughtcrime.securesms.components.settings.app.usernamelinks.main

import org.signal.appsettings.usernamelinks.QrCodeState
import org.signal.appsettings.usernamelinks.UsernameQrCodeColorScheme

/**
 * Represents the UI state of the [UsernameLinkSettingsFragment].
 */
data class UsernameLinkSettingsState(
  val activeTab: ActiveTab,
  val username: String,
  val usernameLinkState: UsernameLinkState,
  val qrCodeState: QrCodeState,
  val qrCodeColorScheme: UsernameQrCodeColorScheme,
  val qrScanResult: QrScanResult? = null,
  val usernameLinkResetResult: UsernameLinkResetResult? = null,
  val indeterminateProgress: Boolean = false,
  /** Whether the sheet for copying or sharing the link is open. */
  val showShareSheet: Boolean = false
) {
  enum class ActiveTab {
    Code,
    Scan
  }
}
