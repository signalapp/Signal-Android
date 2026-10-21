/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.export

import android.net.Uri
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.appsettings.exportaccountdata.ExportAccountDataAction
import org.signal.appsettings.exportaccountdata.ExportAccountDataEvent
import org.signal.appsettings.exportaccountdata.ExportAccountDataFormat
import org.signal.appsettings.exportaccountdata.ExportAccountDataState
import org.signal.appsettings.exportaccountdata.ExportAccountDataState.Dialog
import org.thoughtcrime.securesms.components.settings.app.account.export.ExportAccountDataRepository.GenerateReportResult

@OptIn(ExperimentalCoroutinesApi::class)
class ExportAccountDataViewModelTest {

  companion object {
    private const val LEARN_MORE_URL = "https://support.signal.org/hc/articles/5538911756954"
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  private val repository = mockk<ExportAccountDataRepository>()
  private val reportUri = mockk<Uri>()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    coEvery { repository.downloadAccountDataReport(any()) } returns GenerateReportResult.Success(uri = reportUri, mimeType = "text/plain")
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state exports as TXT with no dialog`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    assertThat(viewModel.state.value).isEqualTo(ExportAccountDataState(format = ExportAccountDataFormat.TXT, dialog = Dialog.None))
  }

  @Test
  fun `NavigateBackClicked leaves the screen`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(ExportAccountDataEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(ExportAccountDataAction.NavigateBack)
  }

  @Test
  fun `LearnMoreClicked opens the link`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(ExportAccountDataEvent.LearnMoreClicked(LEARN_MORE_URL))

    assertThat(actions).containsExactly(ExportAccountDataAction.OpenBrowserLink(LEARN_MORE_URL))
  }

  @Test
  fun `FormatSelected changes the format`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ExportAccountDataEvent.FormatSelected(ExportAccountDataFormat.JSON))
    assertThat(viewModel.state.value.format).isEqualTo(ExportAccountDataFormat.JSON)

    viewModel.onEvent(ExportAccountDataEvent.FormatSelected(ExportAccountDataFormat.TXT))
    assertThat(viewModel.state.value.format).isEqualTo(ExportAccountDataFormat.TXT)
  }

  @Test
  fun `ExportReportClicked asks for confirmation before downloading anything`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ExportAccountDataEvent.ExportReportClicked)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmExport)
    coVerify(exactly = 0) { repository.downloadAccountDataReport(any()) }
  }

  @Test
  fun `DialogDismissed closes the confirmation without downloading anything`() = runTest(testDispatcher) {
    val viewModel = createViewModel()

    viewModel.onEvent(ExportAccountDataEvent.ExportReportClicked)
    viewModel.onEvent(ExportAccountDataEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    coVerify(exactly = 0) { repository.downloadAccountDataReport(any()) }
  }

  @Test
  fun `ExportConfirmed shows progress while the report is generated`() = runTest(testDispatcher) {
    val result = CompletableDeferred<GenerateReportResult>()
    coEvery { repository.downloadAccountDataReport(any()) } coAnswers { result.await() }

    val viewModel = createViewModel()

    viewModel.onEvent(ExportAccountDataEvent.ExportReportClicked)
    viewModel.onEvent(ExportAccountDataEvent.ExportConfirmed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.GeneratingReport)

    result.complete(GenerateReportResult.Success(uri = reportUri, mimeType = "text/plain"))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `ExportConfirmed shares the report in the selected format`() = runTest(testDispatcher) {
    coEvery { repository.downloadAccountDataReport(ExportAccountDataFormat.JSON) } returns GenerateReportResult.Success(uri = reportUri, mimeType = "application/json")

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(ExportAccountDataEvent.FormatSelected(ExportAccountDataFormat.JSON))
    viewModel.onEvent(ExportAccountDataEvent.ExportReportClicked)
    viewModel.onEvent(ExportAccountDataEvent.ExportConfirmed)

    coVerify(exactly = 1) { repository.downloadAccountDataReport(ExportAccountDataFormat.JSON) }
    assertThat(actions).containsExactly(ExportAccountDataAction.ShareGeneratedReport(uri = reportUri, mimeType = "application/json"))
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `a failed export tells the user and shares nothing`() = runTest(testDispatcher) {
    coEvery { repository.downloadAccountDataReport(any()) } returns GenerateReportResult.Failed

    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(ExportAccountDataEvent.ExportReportClicked)
    viewModel.onEvent(ExportAccountDataEvent.ExportConfirmed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ReportGenerationFailed)
    assertThat(actions).isEmpty()

    viewModel.onEvent(ExportAccountDataEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `ExportConfirmed does nothing when the confirmation isn't showing`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(ExportAccountDataEvent.ExportConfirmed)

    coVerify(exactly = 0) { repository.downloadAccountDataReport(any()) }
    assertThat(actions).isEmpty()
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  private fun createViewModel(): ExportAccountDataViewModel = ExportAccountDataViewModel(repository)

  private fun TestScope.collectActions(actions: Flow<ExportAccountDataAction>): List<ExportAccountDataAction> {
    val collected = mutableListOf<ExportAccountDataAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
