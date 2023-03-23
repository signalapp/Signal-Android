package org.thoughtcrime.securesms.components.settings.app.account.export

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore

class ExportAccountDataViewModel(
  private val repository: ExportAccountDataRepository = ExportAccountDataRepository()
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(ExportAccountDataViewModel::class.java)
  }

  private val disposables = CompositeDisposable()

  private val _state = mutableStateOf(
    ExportAccountDataState(reportDownloaded = false, downloadInProgress = false, exportAsJson = false)
  )

  val state: State<ExportAccountDataState> = _state

  init {
    _state.value = _state.value.copy(reportDownloaded = SignalStore.account().hasAccountDataReport())
  }

  fun onGenerateReport(): ExportAccountDataRepository.ExportedReport = repository.generateAccountDataReport(state.value.exportAsJson)
  fun onDownloadReport() {
    _state.value = _state.value.copy(downloadInProgress = true)
    disposables += repository.downloadAccountDataReport()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe({
        _state.value = _state.value.copy(downloadInProgress = false, reportDownloaded = true)
      }, { throwable ->
        Log.e(TAG, throwable)
        _state.value = _state.value.copy(downloadInProgress = false, showDownloadFailedDialog = true)
      })
  }

  fun setExportAsJson() {
    _state.value = _state.value.copy(exportAsJson = true)
  }

  fun setExportAsTxt() {
    _state.value = _state.value.copy(exportAsJson = false)
  }

  fun showDeleteConfirmationDialog() {
    _state.value = _state.value.copy(showDeleteDialog = true)
  }

  fun dismissDeleteConfirmationDialog() {
    _state.value = _state.value.copy(showDeleteDialog = false)
  }

  fun dismissDownloadErrorDialog() {
    _state.value = _state.value.copy(showDownloadFailedDialog = false)
  }

  fun showExportConfirmationDialog() {
    _state.value = _state.value.copy(showExportDialog = true)
  }

  fun dismissExportConfirmationDialog() {
    _state.value = _state.value.copy(showExportDialog = false)
  }

  fun deleteReport() {
    SignalStore.account().deleteAccountDataReport()
    _state.value = _state.value.copy(reportDownloaded = false)
  }

  override fun onCleared() {
    disposables.dispose()
  }
}
