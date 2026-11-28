package org.thoughtcrime.securesms.components.settings.conversation.sounds

import androidx.compose.runtime.Composable
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.components.settings.app.notifications.muted.MutedNotificationsEntry

/**
 * Fragment to control while muted settings for a specific chat
 */
class MutedNotificationsFragment : ComposeFragment() {

  @Composable
  override fun FragmentContent() {
    MutedNotificationsEntry(
      recipientId = MutedNotificationsFragmentArgs.fromBundle(requireArguments()).recipientId,
      parentEventEmitter = { event ->
        when (event) {
          is AppSettingsNavigationEvent.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
          else -> error("Unexpected event: $event")
        }
      }
    )
  }
}
