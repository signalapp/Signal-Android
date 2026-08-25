package org.thoughtcrime.securesms.components.settings.app.notifications

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.thoughtcrime.securesms.R

/**
 * Fragment to control default settings when muted
 */
class GlobalMutedNotificationsFragment : ComposeFragment() {

  private val viewModel: MutedNotificationsViewModel by viewModels(
    factoryProducer = { MutedNotificationsViewModel.Factory() }
  )

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffolds.Settings(
      title = stringResource(R.string.MutedNotificationsFragment__while),
      navigationIcon = SignalIcons.ArrowStart.imageVector,
      onNavigationClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close),
      modifier = Modifier.imePadding()
    ) { paddingValues ->
      MutedNotificationScreen(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = Modifier.padding(paddingValues)
      )
    }
  }
}
