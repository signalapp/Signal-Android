package org.thoughtcrime.securesms.components.settings.app.changenumber

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.changenumber.ChangeNumberUtil.getCaptchaArguments
import org.thoughtcrime.securesms.components.settings.app.changenumber.ChangeNumberUtil.getViewModel
import org.thoughtcrime.securesms.registration.VerifyAccountRepository
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.navigation.safeNavigate

private val TAG: String = Log.tag(ChangeNumberVerifyFragment::class.java)

class ChangeNumberVerifyFragment : LoggingFragment(R.layout.fragment_change_phone_number_verify) {
  private lateinit var viewModel: ChangeNumberViewModel

  private var requestingCaptcha: Boolean = false

  private val lifecycleDisposable: LifecycleDisposable = LifecycleDisposable()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    lifecycleDisposable.bindTo(lifecycle)
    viewModel = getViewModel(this)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val toolbar: Toolbar = view.findViewById(R.id.toolbar)
    toolbar.setTitle(R.string.ChangeNumberVerifyFragment__change_number)
    toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

    val status: TextView = view.findViewById(R.id.change_phone_number_verify_status)
    status.text = getString(R.string.ChangeNumberVerifyFragment__verifying_s, viewModel.number.fullFormattedNumber)

    if (!requestingCaptcha || viewModel.hasCaptchaToken()) {
      requestCode()
    } else {
      Toast.makeText(requireContext(), R.string.ChangeNumberVerifyFragment__captcha_required, Toast.LENGTH_SHORT).show()
      findNavController().navigateUp()
    }
  }

  private fun requestCode() {
    lifecycleDisposable += viewModel
      .ensureDecryptionsDrained()
      .onErrorComplete()
      .andThen(viewModel.requestVerificationCode(VerifyAccountRepository.Mode.SMS_WITHOUT_LISTENER))
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { processor ->
        if (processor.hasResult()) {
          findNavController().safeNavigate(R.id.action_changePhoneNumberVerifyFragment_to_changeNumberEnterCodeFragment)
        } else if (processor.localRateLimit()) {
          Log.i(TAG, "Unable to request sms code due to local rate limit")
          findNavController().safeNavigate(R.id.action_changePhoneNumberVerifyFragment_to_changeNumberEnterCodeFragment)
        } else if (processor.captchaRequired()) {
          Log.i(TAG, "Unable to request sms code due to captcha required")
          findNavController().safeNavigate(R.id.action_changePhoneNumberVerifyFragment_to_captchaFragment, getCaptchaArguments())
          requestingCaptcha = true
        } else if (processor.rateLimit()) {
          Log.i(TAG, "Unable to request sms code due to rate limit")
          Toast.makeText(requireContext(), R.string.RegistrationActivity_rate_limited_to_service, Toast.LENGTH_LONG).show()
          findNavController().navigateUp()
        } else {
          Log.w(TAG, "Unable to request sms code", processor.error)
          Toast.makeText(requireContext(), R.string.RegistrationActivity_unable_to_connect_to_service, Toast.LENGTH_LONG).show()
          findNavController().navigateUp()
        }
      }
  }
}
