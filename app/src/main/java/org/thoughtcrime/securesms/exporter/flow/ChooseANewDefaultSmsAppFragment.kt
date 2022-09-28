package org.thoughtcrime.securesms.exporter.flow

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import org.signal.core.util.logging.Log
import org.signal.smsexporter.DefaultSmsHelper
import org.signal.smsexporter.ReleaseSmsAppFailure
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.ChooseANewDefaultSmsAppFragmentBinding

/**
 * Fragment which can launch the user into picking an alternative
 * SMS app, or give them instructions on how to do so manually.
 */
class ChooseANewDefaultSmsAppFragment : Fragment(R.layout.choose_a_new_default_sms_app_fragment) {

  companion object {
    private val TAG = Log.tag(ChooseANewDefaultSmsAppFragment::class.java)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val binding = ChooseANewDefaultSmsAppFragmentBinding.bind(view)

    if (Build.VERSION.SDK_INT < 24) {
      binding.bullet1Text.setText(R.string.ChooseANewDefaultSmsAppFragment__open_your_phones_settings_app)
      binding.bullet2Text.setText(R.string.ChooseANewDefaultSmsAppFragment__navigate_to_apps_default_apps_sms_app)
      binding.continueButton.setText(R.string.ChooseANewDefaultSmsAppFragment__done)
    }

    DefaultSmsHelper.releaseDefaultSms(requireContext()).either(
      onSuccess = {
        binding.continueButton.setOnClickListener { _ -> startActivity(it) }
      },
      onFailure = {
        when (it) {
          ReleaseSmsAppFailure.APP_IS_INELIGIBLE_TO_RELEASE_SMS_SELECTION -> {
            Log.w(TAG, "App is ineligible to release sms selection")
            binding.continueButton.setOnClickListener { requireActivity().finish() }
          }
          ReleaseSmsAppFailure.NO_METHOD_TO_RELEASE_SMS_AVIALABLE -> {
            Log.w(TAG, "We can't navigate the user to a specific spot so we should display instructions instead.")
            binding.continueButton.setOnClickListener { requireActivity().finish() }
          }
        }
      }
    )
  }

  override fun onResume() {
    super.onResume()
    if (!DefaultSmsHelper.isDefaultSms(requireContext())) {
      requireActivity().setResult(Activity.RESULT_OK)
      requireActivity().finish()
    }
  }
}
