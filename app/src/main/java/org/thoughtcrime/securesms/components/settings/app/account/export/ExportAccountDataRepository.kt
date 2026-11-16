package org.thoughtcrime.securesms.components.settings.app.account.export

import android.net.Uri
import arrow.core.getOrElse
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.signal.appsettings.exportaccountdata.ExportAccountDataFormat
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.signal.core.util.serialization.SignalJson
import org.signal.libsignal.net.RequestResult
import org.signal.network.api.AccountApiV2
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.net.SignalNetwork

class ExportAccountDataRepository(
  private val api: AccountApiV2 = SignalNetwork.accountApiV2
) {

  companion object {
    private val TAG = Log.tag(ExportAccountDataRepository::class)
  }

  suspend fun downloadAccountDataReport(format: ExportAccountDataFormat): GenerateReportResult {
    return when (val result = api.accountDataReport()) {
      is RequestResult.Success -> withContext(SignalDispatchers.IO) {
        generateAccountDataReport(result.result, format == ExportAccountDataFormat.JSON)
      }
      is RequestResult.NonSuccess -> {
        Log.w(TAG, "Server did not generate the account data report: ${result.error}")
        GenerateReportResult.Failed
      }
      is RequestResult.RetryableNetworkError -> {
        Log.w(TAG, "Couldn't fetch the account data report.", result.networkError)
        GenerateReportResult.Failed
      }
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Couldn't fetch the account data report.", result.cause)
        GenerateReportResult.Failed
      }
    }
  }

  private fun generateAccountDataReport(report: String, exportAsJson: Boolean): GenerateReportResult {
    val mimeType: String
    val fileName: String
    if (exportAsJson) {
      mimeType = "application/json"
      fileName = "account-data.json"
    } else {
      mimeType = "text/plain"
      fileName = "account-data.txt"
    }

    val tree = SignalJson.decode<JsonElement>(report).getOrElse {
      Log.w(TAG, "The account data report wasn't valid JSON.", it.cause)
      return GenerateReportResult.Failed
    }

    if (tree !is JsonObject) {
      Log.w(TAG, "The account data report wasn't a JSON object.")
      return GenerateReportResult.Failed
    }

    val dataStr = if (exportAsJson) {
      JsonObject(tree - "text").toString()
    } else {
      val text = tree["text"] as? JsonPrimitive
      if (text == null || !text.isString) {
        Log.w(TAG, "The account data report had no text to export.")
        return GenerateReportResult.Failed
      }
      text.content
    }

    val uri = AppDependencies.blobs
      .forData(dataStr.encodeToByteArray())
      .withMimeType(mimeType)
      .withFileName(fileName)
      .createForSingleUseInMemory()

    return GenerateReportResult.Success(uri = uri, mimeType = mimeType)
  }

  sealed interface GenerateReportResult {
    data class Success(val uri: Uri, val mimeType: String) : GenerateReportResult
    data object Failed : GenerateReportResult
  }
}
