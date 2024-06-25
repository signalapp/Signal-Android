package org.thoughtcrime.securesms.components.settings.app.changenumber

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.changenumber.ChangeNumberUtil.changeNumberSuccess
import org.thoughtcrime.securesms.components.settings.app.changenumber.ChangeNumberUtil.getCaptchaArguments
import org.thoughtcrime.securesms.components.settings.app.changenumber.ChangeNumberUtil.getViewModel
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.fragments.BaseEnterSmsCodeFragment
import org.thoughtcrime.securesms.util.navigation.safeNavigate

private val TAG: String = Log.tag(ChangeNumberEnterSmsCodeFragment::class.java)

class ChangeNumberEnterSmsCodeFragment : BaseEnterSmsCodeFragment<ChangeNumberViewModel>(R.layout.fragment_change_number_enter_code) {

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    toolbar.title = viewModel.number.fullFormattedNumber
    toolbar.setNavigationOnClickListener {
      Log.d(TAG, "Toolbar navigation clicked.")
      navigateUp()
    }

    view.findViewById<View>(R.id.verify_header).setOnClickListener(null)

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          Log.d(TAG, "onBackPressed")
          navigateUp()
        }
      }
    )
  }

  private fun navigateUp() {
    if (SignalStore.misc.isChangeNumberLocked) {
      Log.d(TAG, "Change number locked, navigateUp")
      startActivity(ChangeNumberLockActivity.createIntent(requireContext()))
    } else {
      Log.d(TAG, "navigateUp")
      findNavController().navigateUp()
    }
  }

  override fun getViewModel(): ChangeNumberViewModel {
    return getViewModel(this)
  }

  override fun handleSuccessfulVerify() {
    Log.d(TAG, "handleSuccessfulVerify")
    displaySuccess { changeNumberSuccess() }
  }

  override fun navigateToCaptcha() {
    Log.d(TAG, "navigateToCaptcha")
    findNavController().safeNavigate(R.id.action_changeNumberEnterCodeFragment_to_captchaFragment, getCaptchaArguments())
  }

  override fun navigateToRegistrationLock(timeRemaining: Long) {
    Log.d(TAG, "navigateToRegistrationLock")
    findNavController().safeNavigate(ChangeNumberEnterSmsCodeFragmentDirections.actionChangeNumberEnterCodeFragmentToChangeNumberRegistrationLock(timeRemaining))
  }

  override fun navigateToKbsAccountLocked() {
    Log.d(TAG, "navigateToKbsAccountLocked")
    findNavController().safeNavigate(ChangeNumberEnterSmsCodeFragmentDirections.actionChangeNumberEnterCodeFragmentToChangeNumberAccountLocked())
  }
}
