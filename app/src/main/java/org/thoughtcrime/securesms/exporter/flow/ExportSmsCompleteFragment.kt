package org.thoughtcrime.securesms.exporter.flow

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.ExportSmsCompleteFragmentBinding
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Shown when export sms completes.
 */
class ExportSmsCompleteFragment : Fragment(R.layout.export_sms_complete_fragment) {

  val args: ExportSmsCompleteFragmentArgs by navArgs()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val binding = ExportSmsCompleteFragmentBinding.bind(view)

    binding.exportCompleteNext.setOnClickListener { findNavController().safeNavigate(ExportSmsCompleteFragmentDirections.actionExportingSmsMessagesFragmentToChooseANewDefaultSmsAppFragment()) }
    binding.exportCompleteStatus.text = getString(R.string.ExportSmsCompleteFragment__d_of_d_messages_exported, args.exportMessageCount, args.exportMessageCount)
  }
}
