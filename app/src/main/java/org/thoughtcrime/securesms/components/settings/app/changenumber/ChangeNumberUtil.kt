package org.thoughtcrime.securesms.components.settings.app.changenumber

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.registration.fragments.CaptchaFragment
import org.thoughtcrime.securesms.registration.viewmodel.BaseRegistrationViewModel

/**
 * Helpers for various aspects of the change number flow.
 */
object ChangeNumberUtil {
  @JvmStatic
  fun getViewModel(fragment: Fragment): ChangeNumberViewModel {
    val navController = NavHostFragment.findNavController(fragment)
    return ViewModelProvider(
      navController.getViewModelStoreOwner(R.id.app_settings_change_number),
      ChangeNumberViewModel.Factory(navController.getBackStackEntry(R.id.app_settings_change_number))
    ).get(ChangeNumberViewModel::class.java)
  }

  fun getCaptchaArguments(): Bundle {
    return Bundle().apply {
      putSerializable(
        CaptchaFragment.EXTRA_VIEW_MODEL_PROVIDER,
        object : CaptchaFragment.CaptchaViewModelProvider {
          override fun get(fragment: CaptchaFragment): BaseRegistrationViewModel = getViewModel(fragment)
        }
      )
    }
  }

  fun Fragment.changeNumberSuccess() {
    requireActivity().finish()
    requireActivity().startActivity(AppSettingsActivity.home(requireContext(), AppSettingsActivity.ACTION_CHANGE_NUMBER_SUCCESS))
  }
}
