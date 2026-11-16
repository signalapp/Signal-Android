package org.thoughtcrime.securesms.components.settings.app.privacy.screenlock

import androidx.compose.runtime.Composable
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter

/**
 * Lets the user turn on Signal's screen lock and choose how long the app can be in the background before it locks.
 */
class ScreenLockSettingsFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter()

  @Composable
  override fun FragmentContent() {
    ScreenLockSettingsEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }
}
