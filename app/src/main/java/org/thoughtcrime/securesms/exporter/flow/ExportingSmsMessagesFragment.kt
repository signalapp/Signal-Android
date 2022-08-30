package org.thoughtcrime.securesms.exporter.flow

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.signal.smsexporter.SmsExportProgress
import org.signal.smsexporter.SmsExportService
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.ExportingSmsMessagesFragmentBinding
import org.thoughtcrime.securesms.exporter.SignalSmsExportService
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * "Export in progress" fragment which should be displayed
 * when we start exporting messages.
 */
class ExportingSmsMessagesFragment : Fragment(R.layout.exporting_sms_messages_fragment) {

  private val lifecycleDisposable = LifecycleDisposable()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val binding = ExportingSmsMessagesFragmentBinding.bind(view)

    lifecycleDisposable.bindTo(viewLifecycleOwner)
    lifecycleDisposable += SmsExportService.progressState.observeOn(AndroidSchedulers.mainThread()).subscribe {
      when (it) {
        SmsExportProgress.Done -> {
          findNavController().safeNavigate(ExportingSmsMessagesFragmentDirections.actionExportingSmsMessagesFragmentToChooseANewDefaultSmsAppFragment())
        }
        is SmsExportProgress.InProgress -> {
          binding.progress.isIndeterminate = false
          binding.progress.max = it.total
          binding.progress.progress = it.progress
          binding.progressLabel.text = getString(R.string.ExportingSmsMessagesFragment__exporting_d_of_d, it.progress, it.total)
        }
        SmsExportProgress.Init -> binding.progress.isIndeterminate = true
        SmsExportProgress.Starting -> binding.progress.isIndeterminate = true
      }
    }

    SignalSmsExportService.start(requireContext())
  }
}
