package org.thoughtcrime.securesms.components.settings.app.account.export

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ShareCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.signal.core.ui.Buttons
import org.signal.core.ui.Dialogs
import org.signal.core.ui.Rows
import org.signal.core.ui.Scaffolds
import org.signal.core.ui.Texts
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SpanUtil

class ExportAccountDataFragment : ComposeFragment() {

  companion object {
    val TAG = Log.tag(ExportAccountDataFragment::class.java)
  }

  private val viewModel: ExportAccountDataViewModel by viewModels()

  private val disposables = LifecycleDisposable()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    disposables.bindTo(viewLifecycleOwner)
  }

  private fun exportReport() {
    disposables += viewModel.onGenerateReport()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { report ->
        ShareCompat.IntentBuilder(requireContext())
          .setStream(report.uri)
          .setType(report.mimeType)
          .startChooser()
      }
  }

  private fun dismissExportDialog() {
    viewModel.dismissExportConfirmationDialog()
  }

  private fun dismissDownloadErrorDialog() {
    viewModel.dismissDownloadErrorDialog()
  }

  @Preview
  @Composable
  override fun FragmentContent() {
    val state: ExportAccountDataState by viewModel.state

    val onNavigationClick: () -> Unit = remember {
      { findNavController().popBackStack() }
    }

    Scaffolds.Settings(
      title = stringResource(id = R.string.AccountSettingsFragment__request_account_data),
      onNavigationClick = onNavigationClick,
      navigationIconPainter = painterResource(id = R.drawable.ic_arrow_left_24),
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close)
    ) { contentPadding ->
      Surface(
        modifier = Modifier
          .padding(contentPadding)
          .wrapContentSize()
      ) {
        LazyColumn(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
          item {
            Image(
              painter = painterResource(id = R.drawable.export_account_data),
              contentDescription = stringResource(R.string.ExportAccountDataFragment__your_account_data),
              modifier = Modifier.padding(top = 47.dp)
            )
          }

          item {
            Text(
              text = stringResource(id = R.string.ExportAccountDataFragment__your_account_data),
              style = MaterialTheme.typography.headlineMedium,
              modifier = Modifier.padding(top = 16.dp)
            )
          }

          item {
            val learnMore = stringResource(R.string.ExportAccountDataFragment__learn_more)
            val explanation = stringResource(R.string.ExportAccountDataFragment__export_explanation, learnMore)
            Texts.LinkifiedText(
              textWithUrlSpans = SpanUtil.urlSubsequence(explanation, learnMore, stringResource(R.string.export_account_data_url)),
              onUrlClick = { url ->
                CommunicationActions.openBrowserLink(requireContext(), url)
              },
              modifier = Modifier.padding(top = 12.dp, start = 32.dp, end = 32.dp, bottom = 20.dp),
              style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            )
          }

          item {
            ExportReportOptions(exportAsJson = state.exportAsJson)
          }
        }
        if (state.downloadInProgress) {
          DownloadProgressDialog()
        } else if (state.showDownloadFailedDialog) {
          DownloadFailedDialog()
        } else if (state.showExportDialog) {
          ExportReportConfirmationDialog()
        }
      }
    }
  }

  @Composable
  private fun DownloadProgressDialog() {
    Dialog(
      onDismissRequest = {},
      DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
      Card {
        Box(contentAlignment = Alignment.Center) {
          Column(
            verticalArrangement = Center,
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            CircularProgressIndicator(
              modifier = Modifier
                .padding(top = 50.dp, bottom = 18.dp)
                .size(42.dp)
            )
            Text(text = stringResource(R.string.ExportAccountDataFragment__download_progress), Modifier.padding(bottom = 48.dp, start = 35.dp, end = 35.dp))
          }
        }
      }
    }
  }

  @Composable
  private fun DownloadFailedDialog() {
    Dialogs.SimpleMessageDialog(
      message = stringResource(id = R.string.ExportAccountDataFragment__check_network),
      dismiss = stringResource(id = R.string.ExportAccountDataFragment__ok_action),
      title = stringResource(id = R.string.ExportAccountDataFragment__report_generation_failed),
      onDismiss = this::dismissDownloadErrorDialog
    )
  }

  @Composable
  private fun ExportReportConfirmationDialog() {
    Dialogs.SimpleAlertDialog(
      title = stringResource(R.string.ExportAccountDataFragment__export_report_confirmation),
      body = stringResource(R.string.ExportAccountDataFragment__export_report_confirmation_message),
      confirm = stringResource(R.string.ExportAccountDataFragment__export_report_action),
      dismiss = stringResource(R.string.ExportAccountDataFragment__cancel_action),
      onConfirm = this::exportReport,
      onDismiss = this::dismissExportDialog
    )
  }

  @Composable
  private fun ExportReportOptions(exportAsJson: Boolean) {
    Rows.RadioRow(
      selected = !exportAsJson,
      text = stringResource(id = R.string.ExportAccountDataFragment__export_as_txt),
      label = stringResource(id = R.string.ExportAccountDataFragment__export_as_txt_label),
      modifier = Modifier
        .clickable(onClick = viewModel::setExportAsTxt)
        .padding(horizontal = 16.dp)
    )

    Rows.RadioRow(
      selected = exportAsJson,
      text = stringResource(id = R.string.ExportAccountDataFragment__export_as_json),
      label = stringResource(id = R.string.ExportAccountDataFragment__export_as_json_label),
      modifier = Modifier
        .clickable(onClick = viewModel::setExportAsJson)
        .padding(horizontal = 16.dp)
    )

    Buttons.LargeTonal(
      onClick = viewModel::showExportConfirmationDialog,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 24.dp, start = 32.dp, end = 32.dp)
    ) {
      Text(
        text = stringResource(R.string.ExportAccountDataFragment__export_report),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimaryContainer
      )
    }

    Text(
      text = stringResource(id = R.string.ExportAccountDataFragment__report_not_stored_disclaimer),
      style = MaterialTheme.typography.bodySmall,
      textAlign = TextAlign.Start,
      modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 28.dp, bottom = 20.dp)
    )
  }
}
