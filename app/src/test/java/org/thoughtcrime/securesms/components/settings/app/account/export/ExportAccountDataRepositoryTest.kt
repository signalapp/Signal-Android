/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.export

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.appsettings.exportaccountdata.ExportAccountDataFormat
import org.signal.core.util.serialization.SignalJson
import org.signal.libsignal.net.RequestResult
import org.signal.network.api.AccountApiV2
import org.thoughtcrime.securesms.components.settings.app.account.export.ExportAccountDataRepository.GenerateReportResult
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ExportAccountDataRepositoryTest {

  companion object {
    private const val REPORT_TEXT = "Report ID: 4c0ca2aa-151b-4e9e-8bf4-ea2c64345a22\nReport timestamp: 2023-03-22T20:21:24Z\n\n# Account\nPhone number: +14125556950\n"

    private val REPORT = """
      {
        "reportId": "4c0ca2aa-151b-4e9e-8bf4-ea2c64345a22",
        "reportTimestamp": "2023-03-22T20:21:24Z",
        "data": {
          "account": {
            "phoneNumber": "+14125556950",
            "allowSealedSenderFromAnyone": false,
            "findAccountByPhoneNumber": true
          },
          "devices": [
            { "id": 1, "lastSeen": "2023-03-22T00:00:00Z", "created": "2023-03-07T19:37:08Z", "userAgent": "OWA" }
          ]
        },
        "text": "Report ID: 4c0ca2aa-151b-4e9e-8bf4-ea2c64345a22\nReport timestamp: 2023-03-22T20:21:24Z\n\n# Account\nPhone number: +14125556950\n"
      }
    """.trimIndent()
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val api = mockk<AccountApiV2>()
  private val repository = ExportAccountDataRepository(api)

  /** The blob provider is a relaxed mock, so the report only exists as whatever was handed to it. */
  private val writtenReport = slot<ByteArray>()

  @Before
  fun setUp() {
    every { AppDependencies.blobs.forData(capture(writtenReport)) } returns mockk(relaxed = true)
  }

  private fun writtenJson(): JsonObject = SignalJson.json.parseToJsonElement(String(writtenReport.captured)).jsonObject

  @Test
  fun `a TXT export is just the text rendering`() = runTest(testDispatcher) {
    coEvery { api.accountDataReport() } returns RequestResult.Success(REPORT)

    val result = repository.downloadAccountDataReport(ExportAccountDataFormat.TXT) as GenerateReportResult.Success

    assertThat(result.mimeType).isEqualTo("text/plain")
    assertThat(String(writtenReport.captured)).isEqualTo(REPORT_TEXT)
  }

  @Test
  fun `a JSON export is everything except the text rendering`() = runTest(testDispatcher) {
    coEvery { api.accountDataReport() } returns RequestResult.Success(REPORT)

    val result = repository.downloadAccountDataReport(ExportAccountDataFormat.JSON) as GenerateReportResult.Success

    assertThat(result.mimeType).isEqualTo("application/json")
    val json = writtenJson()
    assertThat(json.containsKey("text")).isFalse()
    assertThat(json.containsKey("data")).isTrue()
    assertThat(json.containsKey("reportId")).isTrue()
    assertThat(json.containsKey("reportTimestamp")).isTrue()
  }

  @Test
  fun `a JSON export keeps the rest of the report exactly as the server sent it`() = runTest(testDispatcher) {
    coEvery { api.accountDataReport() } returns RequestResult.Success("""{"reportId":"abc","data":{"big":12345678901234567890,"list":[1,2.50,null,true]},"text":"t"}""")

    repository.downloadAccountDataReport(ExportAccountDataFormat.JSON)

    assertThat(String(writtenReport.captured)).isEqualTo("""{"reportId":"abc","data":{"big":12345678901234567890,"list":[1,2.50,null,true]}}""")
  }

  @Test
  fun `a network error is a failure`() = runTest(testDispatcher) {
    coEvery { api.accountDataReport() } returns RequestResult.RetryableNetworkError(IOException("offline"))

    assertThat(repository.downloadAccountDataReport(ExportAccountDataFormat.TXT)).isEqualTo(GenerateReportResult.Failed)
  }

  @Test
  fun `a rate limit is a failure`() = runTest(testDispatcher) {
    coEvery { api.accountDataReport() } returns RequestResult.NonSuccess(AccountApiV2.AccountDataReportError.RateLimited(null))

    assertThat(repository.downloadAccountDataReport(ExportAccountDataFormat.TXT)).isEqualTo(GenerateReportResult.Failed)
  }

  @Test
  fun `an unauthorized request is a failure`() = runTest(testDispatcher) {
    coEvery { api.accountDataReport() } returns RequestResult.NonSuccess(AccountApiV2.AccountDataReportError.Unauthorized)

    assertThat(repository.downloadAccountDataReport(ExportAccountDataFormat.JSON)).isEqualTo(GenerateReportResult.Failed)
  }

  @Test
  fun `a report that isn't JSON is a failure`() = runTest(testDispatcher) {
    coEvery { api.accountDataReport() } returns RequestResult.Success("not json")

    assertThat(repository.downloadAccountDataReport(ExportAccountDataFormat.TXT)).isEqualTo(GenerateReportResult.Failed)
    assertThat(repository.downloadAccountDataReport(ExportAccountDataFormat.JSON)).isEqualTo(GenerateReportResult.Failed)
    verify(exactly = 0) { AppDependencies.blobs.forData(any<ByteArray>()) }
  }

  @Test
  fun `a report that isn't a JSON object is a failure`() = runTest(testDispatcher) {
    coEvery { api.accountDataReport() } returns RequestResult.Success("""["text"]""")

    assertThat(repository.downloadAccountDataReport(ExportAccountDataFormat.TXT)).isEqualTo(GenerateReportResult.Failed)
    assertThat(repository.downloadAccountDataReport(ExportAccountDataFormat.JSON)).isEqualTo(GenerateReportResult.Failed)
    verify(exactly = 0) { AppDependencies.blobs.forData(any<ByteArray>()) }
  }

  @Test
  fun `a TXT export of a report with no text is a failure`() = runTest(testDispatcher) {
    coEvery { api.accountDataReport() } returns RequestResult.Success("""{ "reportId": "4c0ca2aa-151b-4e9e-8bf4-ea2c64345a22" }""")

    assertThat(repository.downloadAccountDataReport(ExportAccountDataFormat.TXT)).isEqualTo(GenerateReportResult.Failed)
    verify(exactly = 0) { AppDependencies.blobs.forData(any<ByteArray>()) }
  }

  @Test
  fun `a TXT export of a report whose text isn't a string is a failure`() = runTest(testDispatcher) {
    coEvery { api.accountDataReport() } returns RequestResult.Success("""{ "text": { "account": {} } }""")

    assertThat(repository.downloadAccountDataReport(ExportAccountDataFormat.TXT)).isEqualTo(GenerateReportResult.Failed)
    verify(exactly = 0) { AppDependencies.blobs.forData(any<ByteArray>()) }
  }

  @Test
  fun `a TXT export of a report whose text is a number is a failure`() = runTest(testDispatcher) {
    coEvery { api.accountDataReport() } returns RequestResult.Success("""{ "text": 5 }""")

    assertThat(repository.downloadAccountDataReport(ExportAccountDataFormat.TXT)).isEqualTo(GenerateReportResult.Failed)
    verify(exactly = 0) { AppDependencies.blobs.forData(any<ByteArray>()) }
  }

  @Test
  fun `a JSON export of a report with no text still succeeds`() = runTest(testDispatcher) {
    coEvery { api.accountDataReport() } returns RequestResult.Success("""{ "reportId": "4c0ca2aa-151b-4e9e-8bf4-ea2c64345a22" }""")

    val result = repository.downloadAccountDataReport(ExportAccountDataFormat.JSON)

    assertThat(result).isInstanceOf(GenerateReportResult.Success::class)
    assertThat(writtenJson().containsKey("reportId")).isTrue()
  }
}
