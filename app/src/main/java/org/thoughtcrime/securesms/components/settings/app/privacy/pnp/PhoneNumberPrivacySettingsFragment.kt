package org.thoughtcrime.securesms.components.settings.app.privacy.pnp

import androidx.compose.runtime.Composable
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter

/**
 * Lets the user choose who can see their phone number and who can find them by it.
 */
class PhoneNumberPrivacySettingsFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter()

  @Composable
  override fun FragmentContent() {
    PhoneNumberPrivacySettingsEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }
}
