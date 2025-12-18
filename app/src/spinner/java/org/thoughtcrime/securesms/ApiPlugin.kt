/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.signal.core.util.logging.Log
import org.signal.spinner.Plugin
import org.signal.spinner.PluginResult
import org.whispersystems.signalservice.internal.util.JsonUtil

class ApiPlugin : Plugin {
  companion object {
    private val TAG = Log.tag(ApiPlugin::class.java)
    const val PATH = "/api"
  }

  override val name: String = "APIs"
  override val path: String = PATH

  private val apis = mapOf(
    "localBackups" to ::localBackups
  )

  override fun get(parameters: Map<String, List<String>>): PluginResult {
    val api = parameters["api"]?.firstOrNull()

    if (api == null) {
      val apiButtons = apis.keys.joinToString("\n") { apiName ->
        """
          <div style="margin-bottom: 20px;">
            <button id="btn_$apiName" style="padding: 10px; margin-right: 10px;">Call $PATH?api=$apiName</button>
            <span id="result_$apiName" style="font-weight: bold;"></span>
          </div>
        """.trimIndent()
      }

      val apiScripts = apis.keys.joinToString("\n") { apiName ->
        """
          document.getElementById('btn_$apiName').addEventListener('click', async function() {
            const resultSpan = document.getElementById('result_$apiName');
            resultSpan.textContent = 'Loading...';

            try {
              const response = await fetch('$PATH?api=$apiName');
              if (!response.ok) {
                const errorText = await response.text();
                resultSpan.textContent = 'Error: ' + errorText;
                return;
              }
              const data = await response.json();
              resultSpan.textContent = 'Result: ' + JSON.stringify(data, null, 2);
            } catch (error) {
              resultSpan.textContent = 'Error: ' + error.message;
            }
          });
        """.trimIndent()
      }

      val html = """
        <h3>Available APIs</h3>
        $apiButtons

        <script>
          $apiScripts
        </script>
      """.trimIndent()

      return PluginResult.RawHtmlResult(html)
    }

    return apis[api]?.invoke(parameters) ?: PluginResult.ErrorResult.notFound(message = "not found")
  }

  private fun localBackups(parameters: Map<String, List<String>>): PluginResult {
    // Check if cache bust is requested
    val cacheBust = parameters["cacheBust"]?.firstOrNull() == "true"
    if (cacheBust) {
      Log.i(TAG, "Cache bust requested, clearing local backups cache")
      PluginCache.clearBackupCache()
    }

    if (PluginCache.localBackups != null) {
      return PluginResult.JsonResult(JsonUtil.toJson(PluginCache.localBackups))
    }

    val fs = PluginCache.getArchiveFileSystem() ?: return PluginResult.ErrorResult(message = "Unable to load archive file system! Ensure backup directory is configured.")

    val snapshots = fs.listSnapshots()

    PluginCache.localBackups = LocalBackups(
      snapshots.map { LocalBackup(name = "${it.name} - ${it.timestamp}", it.timestamp) }
    )

    return PluginResult.JsonResult(JsonUtil.toJson(PluginCache.localBackups))
  }

  data class LocalBackups @JsonCreator constructor(@field:JsonProperty val backups: List<LocalBackup>)

  data class LocalBackup @JsonCreator constructor(@field:JsonProperty val name: String, @field:JsonProperty val timestamp: Long)
}
