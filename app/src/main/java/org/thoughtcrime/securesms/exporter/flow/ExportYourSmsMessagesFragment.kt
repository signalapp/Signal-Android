package org.thoughtcrime.securesms.exporter.flow

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.signal.smsexporter.BecomeSmsAppFailure
import org.signal.smsexporter.DefaultSmsHelper
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.ExportYourSmsMessagesFragmentBinding
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * "Welcome" screen for exporting sms
 */
class ExportYourSmsMessagesFragment : Fragment(R.layout.export_your_sms_messages_fragment) {

  companion object {
    private val REQUEST_CODE = 1
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val binding = ExportYourSmsMessagesFragmentBinding.bind(view)

    binding.toolbar.setOnClickListener {
      requireActivity().finish()
    }

    DefaultSmsHelper.becomeDefaultSms(requireContext()).either(
      onSuccess = {
        binding.continueButton.setOnClickListener { _ ->
          startActivityForResult(it, REQUEST_CODE)
        }
      },
      onFailure = {
        when (it) {
          BecomeSmsAppFailure.ALREADY_DEFAULT_SMS -> {
            binding.continueButton.setOnClickListener {
              navigateToExporter()
            }
          }
          BecomeSmsAppFailure.ROLE_IS_NOT_AVAILABLE -> {
            error("Should never happen.")
          }
        }
      }
    )
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == REQUEST_CODE && DefaultSmsHelper.isDefaultSms(requireContext())) {
      navigateToExporter()
    }
  }

  private fun navigateToExporter() {
    findNavController().safeNavigate(ExportYourSmsMessagesFragmentDirections.actionExportYourSmsMessagesFragmentToExportingSmsMessagesFragment())
  }
}
