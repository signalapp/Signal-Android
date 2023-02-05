package org.thoughtcrime.securesms.exporter.flow

import android.content.Context
import android.os.Bundle
import android.text.format.Formatter
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.signal.core.util.concurrent.SimpleTask
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.SmsExportDirections
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.databinding.ExportSmsPartiallyCompleteFragmentBinding
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Fragment shown when some messages exported and some failed.
 */
class ExportSmsPartiallyCompleteFragment : LoggingFragment(R.layout.export_sms_partially_complete_fragment) {

  private val args: ExportSmsPartiallyCompleteFragmentArgs by navArgs()

  override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
    val inflater = super.onGetLayoutInflater(savedInstanceState)
    val contextThemeWrapper: Context = ContextThemeWrapper(requireContext(), R.style.Signal_DayNight)
    return inflater.cloneInContext(contextThemeWrapper)
  }

  @Suppress("UsePropertyAccessSyntax")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val binding = ExportSmsPartiallyCompleteFragmentBinding.bind(view)

    val exportSuccessCount = args.exportMessageCount - args.exportMessageFailureCount
    binding.exportCompleteStatus.text = resources.getQuantityString(R.plurals.ExportSmsCompleteFragment__d_of_d_messages_exported, args.exportMessageCount, exportSuccessCount, args.exportMessageCount)
    binding.retryButton.setOnClickListener { findNavController().safeNavigate(SmsExportDirections.actionDirectToExportYourSmsMessagesFragment()) }
    binding.continueButton.setOnClickListener { findNavController().safeNavigate(SmsExportDirections.actionDirectToChooseANewDefaultSmsAppFragment()) }
    binding.bullet3Text.apply {
      setLinkColor(ContextCompat.getColor(requireContext(), R.color.signal_colorPrimary))
      setLearnMoreVisible(true, R.string.ExportSmsPartiallyComplete__contact_us)
      setOnLinkClickListener {
        findNavController().safeNavigate(SmsExportDirections.actionDirectToHelpFragment())
      }
    }

    SimpleTask.runWhenValid(
      viewLifecycleOwner.lifecycle,
      { SignalDatabase.messages.getUnexportedInsecureMessagesEstimatedSize() + SignalDatabase.messages.getUnexportedInsecureMessagesEstimatedSize() },
      { totalSize ->
        binding.bullet1Text.setText(getString(R.string.ExportSmsPartiallyComplete__ensure_you_have_an_additional_s_free_on_your_phone_to_export_your_messages, Formatter.formatFileSize(requireContext(), totalSize)))
      }
    )
  }
}
