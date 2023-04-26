package org.thoughtcrime.securesms.components.settings.app.account.export

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.subjects.MaybeSubject
import org.signal.core.util.logging.Log

class ExportAccountDataViewModel(
  private val repository: ExportAccountDataRepository = ExportAccountDataRepository()
) : ViewModel() {

  companion object {
    private val TAG = Log.tag(ExportAccountDataViewModel::class.java)
  }

  private val disposables = CompositeDisposable()

  private val _state = mutableStateOf(
    ExportAccountDataState(downloadInProgress = false, exportAsJson = false)
  )

  val state: State<ExportAccountDataState> = _state

  fun onGenerateReport(): Maybe<ExportAccountDataRepository.ExportedReport> {
    _state.value = _state.value.copy(downloadInProgress = true)
    val maybe = MaybeSubject.create<ExportAccountDataRepository.ExportedReport>()
    disposables += repository.downloadAccountDataReport(state.value.exportAsJson)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe({ report ->
        _state.value = _state.value.copy(downloadInProgress = false)
        maybe.onSuccess(report)
      }, { throwable ->
        Log.e(TAG, throwable)
        _state.value = _state.value.copy(downloadInProgress = false, showDownloadFailedDialog = true)
        maybe.onComplete()
      })
    return maybe
  }

  fun setExportAsJson() {
    _state.value = _state.value.copy(exportAsJson = true)
  }

  fun setExportAsTxt() {
    _state.value = _state.value.copy(exportAsJson = false)
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

  override fun onCleared() {
    disposables.dispose()
  }
}
