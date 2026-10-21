package org.thoughtcrime.securesms.components.settings.app.account.export

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.signal.appsettings.exportaccountdata.ExportAccountDataAction
import org.signal.appsettings.exportaccountdata.ExportAccountDataEvent
import org.signal.appsettings.exportaccountdata.ExportAccountDataState
import org.signal.appsettings.exportaccountdata.ExportAccountDataState.Dialog
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.components.settings.app.account.export.ExportAccountDataRepository.GenerateReportResult

class ExportAccountDataViewModel(
  private val repository: ExportAccountDataRepository = ExportAccountDataRepository()
) : EventDrivenViewModel<ExportAccountDataEvent>(TAG) {

  companion object {
    private val TAG = Log.tag(ExportAccountDataViewModel::class.java)
  }

  private val _state = MutableStateFlow(ExportAccountDataState())
  private val _actions = Channel<ExportAccountDataAction>(Channel.BUFFERED)

  val state: StateFlow<ExportAccountDataState> = _state.asStateFlow()
  val actions: Flow<ExportAccountDataAction> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: ExportAccountDataEvent) {
    when (event) {
      is ExportAccountDataEvent.ExportReportClicked -> {
        _state.update { it.copy(dialog = Dialog.ConfirmExport) }
      }
      is ExportAccountDataEvent.ExportConfirmed -> {
        if (_state.value.dialog != Dialog.ConfirmExport) {
          return
        }
        onGenerateReport()
      }
      is ExportAccountDataEvent.DialogDismissed -> {
        _state.update { it.copy(dialog = Dialog.None) }
      }
      is ExportAccountDataEvent.FormatSelected -> {
        _state.update { it.copy(format = event.format) }
      }
      is ExportAccountDataEvent.LearnMoreClicked -> {
        _actions.send(ExportAccountDataAction.OpenBrowserLink(event.url))
      }
      ExportAccountDataEvent.NavigateBackClicked -> {
        _actions.send(ExportAccountDataAction.NavigateBack)
      }
    }
  }

  private suspend fun onGenerateReport() {
    _state.update { it.copy(dialog = Dialog.GeneratingReport) }
    when (val result = repository.downloadAccountDataReport(_state.value.format)) {
      is GenerateReportResult.Success -> {
        _state.update { it.copy(dialog = Dialog.None) }
        _actions.send(ExportAccountDataAction.ShareGeneratedReport(uri = result.uri, mimeType = result.mimeType))
      }
      GenerateReportResult.Failed -> {
        _state.update { it.copy(dialog = Dialog.ReportGenerationFailed) }
      }
    }
  }
}
