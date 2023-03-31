package org.thoughtcrime.securesms.components.settings.app.account.export

import android.net.Uri
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.util.JsonUtils
import org.whispersystems.signalservice.api.SignalServiceAccountManager
import java.io.IOException

class ExportAccountDataRepository(
  private val accountManager: SignalServiceAccountManager = ApplicationDependencies.getSignalServiceAccountManager()
) {

  fun downloadAccountDataReport(exportAsJson: Boolean): Single<ExportedReport> {
    return Single.create {
      try {
        it.onSuccess(generateAccountDataReport(accountManager.accountDataReport, exportAsJson))
      } catch (e: IOException) {
        it.onError(e)
      }
    }.subscribeOn(Schedulers.io())
  }

  private fun generateAccountDataReport(report: String, exportAsJson: Boolean): ExportedReport {
    val mimeType: String
    val fileName: String
    if (exportAsJson) {
      mimeType = "application/json"
      fileName = "account-data.json"
    } else {
      mimeType = "text/plain"
      fileName = "account-data.txt"
    }

    val tree: JsonNode = JsonUtils.getMapper().readTree(report)
    val dataStr = if (exportAsJson) {
      (tree as ObjectNode).remove("text")
      tree.toString()
    } else {
      tree["text"].asText()
    }

    val uri = BlobProvider.getInstance()
      .forData(dataStr.encodeToByteArray())
      .withMimeType(mimeType)
      .withFileName(fileName)
      .createForSingleUseInMemory()

    return ExportedReport(mimeType = mimeType, uri = uri)
  }

  data class ExportedReport(val mimeType: String, val uri: Uri)
}
