package org.thoughtcrime.securesms.components.settings.app.usernamelinks.colorpicker

import androidx.compose.runtime.Composable
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter

/**
 * Fragment host for [UsernameLinkQrColorPickerEntry] until the username link moves to Nav3.
 */
class UsernameLinkQrColorPickerFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter()

  @Composable
  override fun FragmentContent() {
    UsernameLinkQrColorPickerEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }
}
