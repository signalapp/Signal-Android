package org.thoughtcrime.securesms.components.settings.app.changenumber

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import org.thoughtcrime.securesms.R
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
    findNavController().navigate(R.id.action_pop_app_settings_change_number)
    Toast.makeText(requireContext(), R.string.ChangeNumber__your_phone_number_has_been_changed, Toast.LENGTH_SHORT).show()
  }
}
