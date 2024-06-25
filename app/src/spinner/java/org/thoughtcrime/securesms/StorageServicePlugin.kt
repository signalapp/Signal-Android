package org.thoughtcrime.securesms

import org.signal.spinner.Plugin
import org.signal.spinner.PluginResult
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore

class StorageServicePlugin : Plugin {
  override val name: String = "Storage"
  override val path: String = PATH

  override fun get(): PluginResult {
    val columns = listOf("Type", "Data")
    val rows = mutableListOf<List<String>>()

    val manager = AppDependencies.signalServiceAccountManager
    val storageServiceKey = SignalStore.storageService.orCreateStorageKey
    val storageManifestVersion = manager.storageManifestVersion
    val manifest = manager.getStorageManifestIfDifferentVersion(storageServiceKey, storageManifestVersion - 1).get()
    val signalStorageRecords = manager.readStorageRecords(storageServiceKey, manifest.storageIds)

    for (record in signalStorageRecords) {
      val row = mutableListOf<String>()

      if (record.account.isPresent) {
        row += "Account"
        row += record.account.get().toProto().toString()
      } else if (record.contact.isPresent) {
        row += "Contact"
        row += record.contact.get().toProto().toString()
      } else if (record.groupV1.isPresent) {
        row += "GV1"
        row += record.groupV1.get().toProto().toString()
      } else if (record.groupV2.isPresent) {
        row += "GV2"
        row += record.groupV2.get().toProto().toString()
      } else if (record.storyDistributionList.isPresent) {
        row += "Distribution List"
        row += record.storyDistributionList.get().toProto().toString()
      } else {
        row += "Unknown"
        row += ""
      }
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
