package org.thoughtcrime.securesms.exporter.flow

import android.content.Context
import android.os.Bundle
import android.text.format.Formatter
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import org.signal.smsexporter.SmsExportProgress
import org.signal.smsexporter.SmsExportService
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.ExportingSmsMessagesFragmentBinding
import org.thoughtcrime.securesms.exporter.SignalSmsExportService
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.mb
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

  @Suppress("KotlinConstantConditions")
  override fun onResume() {
    super.onResume()
    navigationDisposable = SmsExportService
      .progressState
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { smsExportProgress ->
        if (smsExportProgress is SmsExportProgress.Done) {
          SmsExportService.clearProgressState()
          if (smsExportProgress.errorCount == 0) {
            findNavController().safeNavigate(ExportingSmsMessagesFragmentDirections.actionExportingSmsMessagesFragmentToExportSmsCompleteFragment(smsExportProgress.total, smsExportProgress.errorCount))
          } else if (smsExportProgress.errorCount == smsExportProgress.total) {
            findNavController().safeNavigate(ExportingSmsMessagesFragmentDirections.actionExportingSmsMessagesFragmentToExportSmsFullErrorFragment(smsExportProgress.total, smsExportProgress.errorCount))
          } else {
            findNavController().safeNavigate(ExportingSmsMessagesFragmentDirections.actionExportingSmsMessagesFragmentToExportSmsPartiallyCompleteFragment(smsExportProgress.total, smsExportProgress.errorCount))
          }
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
        SmsExportProgress.Init -> binding.progress.isIndeterminate = true
        SmsExportProgress.Starting -> binding.progress.isIndeterminate = true
        is SmsExportProgress.InProgress -> {
          binding.progress.isIndeterminate = false
          binding.progress.max = it.total
          binding.progress.progress = it.progress
          binding.progressLabel.text = resources.getQuantityString(R.plurals.ExportingSmsMessagesFragment__exporting_d_of_d, it.total, it.progress, it.total)
        }
        is SmsExportProgress.Done -> Unit
      }
    }

    lifecycleDisposable += ExportingSmsRepository()
      .getSmsExportSizeEstimations()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { (internalFreeSpace, estimatedRequiredSpace) ->
        val adjustedFreeSpace = internalFreeSpace - estimatedRequiredSpace - 100.mb
        if (estimatedRequiredSpace > adjustedFreeSpace) {
          MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ExportingSmsMessagesFragment__you_may_not_have_enough_disk_space)
            .setMessage(getString(R.string.ExportingSmsMessagesFragment__you_need_approximately_s_to_export_your_messages_ensure_you_have_enough_space_before_continuing, Formatter.formatFileSize(requireContext(), estimatedRequiredSpace)))
            .setPositiveButton(R.string.ExportingSmsMessagesFragment__continue_anyway) { _, _ -> SignalSmsExportService.start(requireContext()) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> findNavController().safeNavigate(ExportingSmsMessagesFragmentDirections.actionDirectToExportYourSmsMessagesFragment()) }
            .setCancelable(false)
            .show()
        } else {
          SignalSmsExportService.start(requireContext())
        }
      }
  }
}
