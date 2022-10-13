package org.thoughtcrime.securesms.exporter.flow

import android.content.Context
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
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
  private var navigationDisposable = Disposable.disposed()

  override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
    val inflater = super.onGetLayoutInflater(savedInstanceState)
    val contextThemeWrapper: Context = ContextThemeWrapper(requireContext(), R.style.Signal_DayNight)
    return inflater.cloneInContext(contextThemeWrapper)
  }

  override fun onResume() {
    super.onResume()
    navigationDisposable = SmsExportService
      .progressState
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe {
        if (it is SmsExportProgress.Done) {
          findNavController().safeNavigate(ExportingSmsMessagesFragmentDirections.actionExportingSmsMessagesFragmentToExportSmsCompleteFragment(it.progress))
        }
      }
  }

  override fun onPause() {
    super.onPause()
    navigationDisposable.dispose()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val binding = ExportingSmsMessagesFragmentBinding.bind(view)

    lifecycleDisposable.bindTo(viewLifecycleOwner)
    lifecycleDisposable += SmsExportService.progressState.observeOn(AndroidSchedulers.mainThread()).subscribe {
      when (it) {
        is SmsExportProgress.Done -> Unit
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
