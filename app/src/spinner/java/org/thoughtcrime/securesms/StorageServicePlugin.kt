package org.thoughtcrime.securesms

import org.signal.core.util.Base64
import org.signal.spinner.Plugin
import org.signal.spinner.PluginResult
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore

class StorageServicePlugin : Plugin {
  override val name: String = "Storage"
  override val path: String = PATH

  override fun get(): PluginResult {
    val columns = listOf("Type", "Id", "Data")
    val rows = mutableListOf<List<String>>()

    val manager = AppDependencies.signalServiceAccountManager
    val storageServiceKey = SignalStore.storageService.storageKey
    val storageManifestVersion = manager.storageManifestVersion
    val manifest = manager.getStorageManifestIfDifferentVersion(storageServiceKey, storageManifestVersion - 1).get()
    val signalStorageRecords = manager.readStorageRecords(storageServiceKey, manifest.storageIds)

    for (record in signalStorageRecords) {
      val row = mutableListOf<String>()

      if (record.proto.account != null) {
        row += "Account"
        row += record.proto.account.toString()
      } else if (record.proto.contact != null) {
        row += "Contact"
        row += record.proto.toString()
      } else if (record.proto.groupV1 != null) {
        row += "GV1"
        row += record.proto.toString()
      } else if (record.proto.groupV2 != null) {
        row += "GV2"
        row += record.proto.toString()
      } else if (record.proto.storyDistributionList != null) {
        row += "Distribution List"
        row += record.proto.toString()
      } else if (record.proto.callLink != null) {
        row += "Call Link"
        row += record.proto.callLink.toString()
      } else {
        row += "Unknown"
        row += ""
      }

      row.add(1, Base64.encodeWithPadding(record.id.raw))

      rows += row
    }

    rows.sortBy { it.first() }

    return PluginResult.TableResult(
      columns = columns,
      rows = rows
    )
  }

  companion object {
    const val PATH = "/storage"
  }
}
