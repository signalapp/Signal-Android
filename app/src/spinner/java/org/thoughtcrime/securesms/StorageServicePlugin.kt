package org.thoughtcrime.securesms

import org.signal.core.util.Base64
import org.signal.core.util.nullIfBlank
import org.signal.network.service.StorageServiceService
import org.signal.spinner.Plugin
import org.signal.spinner.PluginResult
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.net.SignalNetwork
import org.whispersystems.signalservice.api.storage.signalAci
import org.whispersystems.signalservice.api.storage.signalPni

class StorageServicePlugin : Plugin {
  override val name: String = "Storage"
  override val path: String = PATH

  override fun get(parameters: Map<String, List<String>>): PluginResult {
    val columns = listOf("Type", "Id", "Data")
    val rows = mutableListOf<List<String>>()

    val repository = StorageServiceService(SignalNetwork.storageService)
    val storageServiceKey = SignalStore.storageService.storageKey

    val manifest = when (val result = repository.getStorageManifest(storageServiceKey)) {
      is StorageServiceService.ManifestResult.Success -> result.manifest
      else -> return PluginResult.StringResult("Failed to find manifest!")
    }

    val signalStorageRecords = when (val result = repository.readStorageRecords(storageServiceKey, manifest.recordIkm, manifest.storageIds)) {
      is StorageServiceService.StorageRecordResult.Success -> result.records
      else -> return PluginResult.StringResult("Failed to read records!")
    }

    for (record in signalStorageRecords) {
      val row = mutableListOf<String>()

      if (record.proto.account != null) {
        row += "Account"
        row += record.proto.account.toString().prettyPrintProto()
      } else if (record.proto.contact != null) {
        row += "Contact"
        val contact = record.proto.contact!!
        val readable = contact.copy(
          aci = contact.aci.nullIfBlank() ?: contact.signalAci?.toString()?.let { "**$it**" } ?: contact.aci,
          pni = contact.pni.nullIfBlank() ?: contact.signalPni?.toString()?.let { "**$it**" } ?: contact.pni
        )
        row += readable.toString().prettyPrintProto()
      } else if (record.proto.groupV1 != null) {
        row += "GV1"
        row += record.proto.groupV1.toString().prettyPrintProto()
      } else if (record.proto.groupV2 != null) {
        row += "GV2"
        row += record.proto.groupV2.toString().prettyPrintProto()
      } else if (record.proto.storyDistributionList != null) {
        row += "Distribution List"
        row += record.proto.storyDistributionList.toString().prettyPrintProto()
      } else if (record.proto.callLink != null) {
        row += "Call Link"
        row += record.proto.callLink.toString().prettyPrintProto()
      } else if (record.proto.chatFolder != null) {
        row += "Chat Folder"
        row += record.proto.chatFolder.toString().prettyPrintProto()
      } else if (record.proto.notificationProfile != null) {
        row += "Notification Profile"
        row += record.proto.notificationProfile.toString().prettyPrintProto()
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

private fun String.prettyPrintProto(): String {
  val out = StringBuilder(length + length / 4)
  var indent = 0
  var compactDepth = 0
  fun newline() {
    out.append('\n').append("  ".repeat(indent))
  }
  var i = 0
  while (i < length) {
    val c = this[i]
    when (c) {
      '{', '[' -> {
        val compact = c == '[' && this.regionMatches(i + 1, "hex", 0, 3, ignoreCase = true)
        if (compact) {
          compactDepth++
          out.append(c)
        } else {
          indent++
          out.append(c)
          newline()
        }
      }
      '}', ']' -> {
        if (compactDepth > 0 && c == ']') {
          compactDepth--
          out.append(c)
        } else {
          indent = (indent - 1).coerceAtLeast(0)
          val opener = if (c == '}') '{' else '['
          while (out.isNotEmpty() && (out.last() == ' ' || out.last() == '\n')) {
            out.deleteCharAt(out.length - 1)
          }
          if (out.isNotEmpty() && out.last() == opener) {
            out.append(c)
          } else {
            newline()
            out.append(c)
          }
        }
      }
      ',' -> {
        out.append(c)
        if (compactDepth == 0) newline()
      }
      ' ' -> if (out.isNotEmpty() && out.last() != '\n' && out.last() != ' ') out.append(c)
      else -> out.append(c)
    }
    i++
  }
  return out.toString()
}
