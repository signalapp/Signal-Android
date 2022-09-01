package org.thoughtcrime.securesms.exporter.flow

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.signal.smsexporter.BecomeSmsAppFailure
import org.signal.smsexporter.DefaultSmsHelper
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.SetSignalAsDefaultSmsAppFragmentBinding
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class SetSignalAsDefaultSmsAppFragment : Fragment(R.layout.set_signal_as_default_sms_app_fragment) {
  companion object {
    private const val REQUEST_CODE = 1
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val binding = SetSignalAsDefaultSmsAppFragmentBinding.bind(view)

    binding.continueButton.setOnClickListener {
      DefaultSmsHelper.becomeDefaultSms(requireContext()).either(
        onSuccess = {
          startActivityForResult(it, REQUEST_CODE)
        },
        onFailure = {
          when (it) {
            BecomeSmsAppFailure.ALREADY_DEFAULT_SMS -> navigateToExporter()
            BecomeSmsAppFailure.ROLE_IS_NOT_AVAILABLE -> error("Should never happen")
          }
        }
      )
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == REQUEST_CODE && DefaultSmsHelper.isDefaultSms(requireContext())) {
      navigateToExporter()
    }
  }

  private fun navigateToExporter() {
    findNavController().safeNavigate(SetSignalAsDefaultSmsAppFragmentDirections.actionSetSignalAsDefaultSmsAppFragmentToExportingSmsMessagesFragment())
  }
}
