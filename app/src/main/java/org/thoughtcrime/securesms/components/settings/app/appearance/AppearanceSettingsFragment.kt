package org.thoughtcrime.securesms.components.settings.app.appearance

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.appearance.AppearanceSettingsAction
import org.signal.appsettings.appearance.AppearanceSettingsEvent
import org.signal.appsettings.appearance.AppearanceSettingsScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appearance.navbar.ChooseNavigationBarStyleFragment
import org.thoughtcrime.securesms.util.SplashScreenUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Allows the user to change language, theme, etc. from application settings.
 */
class AppearanceSettingsFragment : ComposeFragment() {

  private val viewModel: AppearanceSettingsViewModel by viewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    childFragmentManager.setFragmentResultListener(ChooseNavigationBarStyleFragment.REQUEST_KEY, viewLifecycleOwner) { key, bundle ->
      if (bundle.getBoolean(key, false)) {
        viewModel.onEvent(AppearanceSettingsEvent.NavigationBarSizeChanged)
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { handleAction(it) }

    AppearanceSettingsScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: AppearanceSettingsAction) {
    when (action) {
      AppearanceSettingsAction.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
      AppearanceSettingsAction.NavigateToChatColorAndWallpaper -> findNavController().safeNavigate(R.id.action_appearanceSettings_to_wallpaperActivity)
      AppearanceSettingsAction.NavigateToAppIcon -> findNavController().safeNavigate(R.id.action_appearanceSettings_to_appIconActivity)
      AppearanceSettingsAction.ShowNavigationBarSizePicker -> ChooseNavigationBarStyleFragment().show(childFragmentManager, null)
      is AppearanceSettingsAction.ApplySplashScreenTheme -> SplashScreenUtil.setSplashScreenThemeIfNecessary(activity, action.theme.toSettingsTheme())
    }
  }
}
