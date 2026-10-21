/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.exportaccountdata

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import org.signal.appsettings.R
import org.signal.appsettings.exportaccountdata.ExportAccountDataState.Dialog
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.SignalPreviewWrapper
import org.signal.core.ui.compose.Texts
import org.signal.core.ui.util.SpanUtil
import org.signal.core.ui.R as CoreUiR

object ExportAccountDataTestTags {
  const val SCROLLER = "scroller"
  const val ROW_FORMAT_TXT = "row-format-txt"
  const val ROW_FORMAT_JSON = "row-format-json"
  const val BUTTON_EXPORT_REPORT = "button-export-report"
  const val DIALOG_CONFIRM_EXPORT = "dialog-confirm-export"
  const val DIALOG_GENERATING_REPORT = "dialog-generating-report"
  const val DIALOG_REPORT_GENERATION_FAILED = "dialog-report-generation-failed"
}

@Composable
fun ExportAccountDataScreen(
  state: ExportAccountDataState,
  onEvent: (ExportAccountDataEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffolds.Settings(
    title = stringResource(id = R.string.AccountSettingsFragment__request_account_data),
    onNavigationClick = { onEvent(ExportAccountDataEvent.NavigateBackClicked) },
    navigationIcon = SignalIcons.ArrowStart.imageVector,
    navigationContentDescription = stringResource(id = CoreUiR.string.CloseButton__content_description)
  ) { contentPadding ->
    Surface(
      modifier = Modifier
        .padding(contentPadding)
        .wrapContentSize()
    ) {
      LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .fillMaxWidth()
          .testTag(ExportAccountDataTestTags.SCROLLER)
      ) {
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
            onUrlClick = { url -> onEvent(ExportAccountDataEvent.LearnMoreClicked(url)) },
            modifier = Modifier.padding(top = 12.dp, start = 32.dp, end = 32.dp, bottom = 20.dp),
            style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
          )
        }

        item {
          ExportReportOptions(state.format, onEvent)
        }
      }

      when (state.dialog) {
        ExportAccountDataState.Dialog.None -> Unit
        ExportAccountDataState.Dialog.ConfirmExport -> ExportReportConfirmationDialog(onEvent)
        ExportAccountDataState.Dialog.GeneratingReport -> DownloadProgressDialog()
        ExportAccountDataState.Dialog.ReportGenerationFailed -> DownloadFailedDialog(onEvent)
      }
    }
  }
}

@Composable
private fun ExportReportOptions(selectedFormat: ExportAccountDataFormat, onEvent: (ExportAccountDataEvent) -> Unit) {
  Rows.RadioRow(
    selected = selectedFormat == ExportAccountDataFormat.TXT,
    text = stringResource(id = R.string.ExportAccountDataFragment__export_as_txt),
    label = stringResource(id = R.string.ExportAccountDataFragment__export_as_txt_label),
    modifier = Modifier
      .clickable(onClick = { onEvent(ExportAccountDataEvent.FormatSelected(ExportAccountDataFormat.TXT)) })
      .padding(horizontal = 16.dp)
      .testTag(ExportAccountDataTestTags.ROW_FORMAT_TXT)
  )

  Rows.RadioRow(
    selected = selectedFormat == ExportAccountDataFormat.JSON,
    text = stringResource(id = R.string.ExportAccountDataFragment__export_as_json),
    label = stringResource(id = R.string.ExportAccountDataFragment__export_as_json_label),
    modifier = Modifier
      .clickable(onClick = { onEvent(ExportAccountDataEvent.FormatSelected(ExportAccountDataFormat.JSON)) })
      .padding(horizontal = 16.dp)
      .testTag(ExportAccountDataTestTags.ROW_FORMAT_JSON)
  )

  Buttons.LargeTonal(
    onClick = { onEvent(ExportAccountDataEvent.ExportReportClicked) },
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 24.dp, start = 32.dp, end = 32.dp)
      .testTag(ExportAccountDataTestTags.BUTTON_EXPORT_REPORT)
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

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ExportAccountDataScreenJsonPreview() {
  ExportAccountDataScreen(state = ExportAccountDataState(format = ExportAccountDataFormat.JSON), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ExportAccountDataScreenConfirmExportPreview() {
  ExportAccountDataScreen(state = ExportAccountDataState(dialog = Dialog.ConfirmExport), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ExportAccountDataScreenGeneratingReportPreview() {
  ExportAccountDataScreen(state = ExportAccountDataState(dialog = Dialog.GeneratingReport), onEvent = {})
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun ExportAccountDataScreenGenerationFailedPreview() {
  ExportAccountDataScreen(state = ExportAccountDataState(dialog = Dialog.ReportGenerationFailed), onEvent = {})
}
