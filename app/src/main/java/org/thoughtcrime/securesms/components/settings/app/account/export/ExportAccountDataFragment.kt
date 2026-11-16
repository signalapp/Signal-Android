package org.thoughtcrime.securesms.components.settings.app.account.export

import androidx.compose.runtime.Composable
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.appSettingsRouter

/**
 * Hosts [ExportAccountDataEntry] under the legacy nav graph.
 */
class ExportAccountDataFragment : ComposeFragment() {

  private val appSettingsRouter by appSettingsRouter()

  @Composable
  override fun FragmentContent() {
    ExportAccountDataEntry(parentEventEmitter = appSettingsRouter::onEvent)
  }
}
