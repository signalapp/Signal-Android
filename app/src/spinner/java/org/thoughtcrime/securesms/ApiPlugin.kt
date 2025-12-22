/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.spinner.Plugin
import org.signal.spinner.PluginResult
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.internal.util.JsonUtil

class ApiPlugin : Plugin {
  companion object {
    private val TAG = Log.tag(ApiPlugin::class.java)
    const val PATH = "/api"
  }

  override val name: String = "APIs"
  override val path: String = PATH

  private val apis = mapOf(
    "localBackups" to ::localBackups,
    "recipient" to ::recipient,
    "thread" to ::thread,
    "message" to ::message
  )

  override fun get(parameters: Map<String, List<String>>): PluginResult {
    val api = parameters["api"]?.firstOrNull()

    if (api == null) {
      val apiButtons = apis.keys.joinToString("\n") { apiName ->
        """
          <div style="margin-bottom: 20px;">
            <button id="btn_$apiName" style="padding: 10px; margin-right: 10px;">Call $PATH?api=$apiName&id=1</button>
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
              const response = await fetch('$PATH?api=$apiName&id=1');
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

  private fun recipient(parameters: Map<String, List<String>>): PluginResult {
    val recipientId = parameters["id"]?.firstOrNull()?.toLongOrNull() ?: return PluginResult.ErrorResult.notFound("recipient not found")

    val recipient = Recipient.resolved(RecipientId.from(recipientId))

    val groupType = when {
      recipient.isMmsGroup -> "MMS"
      recipient.isPushV2Group -> "Signal V2"
      recipient.isPushV1Group -> "Signal V1"
      recipient.isPushGroup -> "Signal"
      else -> null
    }

    return RecipientInfo(
      name = recipient.getDisplayName(AppDependencies.application),
      aci = recipient.aci.map { it.toString() }.orNull(),
      pni = recipient.pni.map { it.toString() }.orNull(),
      e164 = recipient.e164.orNull(),
      username = recipient.username.orNull(),
      nickname = recipient.nickname.toString().takeIf { it.isNotBlank() },
      note = recipient.note,
      about = recipient.about,
      aboutEmoji = recipient.aboutEmoji,
      isBlocked = recipient.isBlocked,
      isSystemContact = recipient.isSystemContact,
      isGroup = recipient.isGroup,
      groupId = recipient.groupId.map { it.toString() }.orNull(),
      groupType = groupType,
      isActiveGroup = recipient.isActiveGroup,
      participantCount = if (recipient.isGroup) recipient.participantIds.size else null
    ).toJsonResult()
  }

  private fun thread(parameters: Map<String, List<String>>): PluginResult {
    val threadId = parameters["id"]?.firstOrNull()?.toLongOrNull() ?: return PluginResult.ErrorResult.notFound("thread not found")

    val threadRecord = SignalDatabase.threads.getThreadRecord(threadId) ?: return PluginResult.ErrorResult(message = "Thread not found")

    return ThreadInfo(
      threadId = threadRecord.threadId,
      recipientName = threadRecord.recipient.getDisplayName(AppDependencies.application),
      recipientId = threadRecord.recipient.id.toLong(),
      unreadCount = threadRecord.unreadCount,
      snippet = threadRecord.body,
      date = threadRecord.date,
      archived = threadRecord.isArchived,
      pinned = threadRecord.isPinned,
      unreadSelfMentionsCount = threadRecord.unreadSelfMentionsCount
    ).toJsonResult()
  }

  private fun message(parameters: Map<String, List<String>>): PluginResult {
    val messageId = parameters["id"]?.firstOrNull()?.toLongOrNull() ?: return PluginResult.ErrorResult.notFound("message not found")

    val messageRecord = try {
      SignalDatabase.messages.getMessageRecord(messageId)
    } catch (e: Exception) {
      return PluginResult.ErrorResult(message = "Message not found: ${e.message}")
    }

    val messageType = when {
      messageRecord.isUpdate -> "Update"
      messageRecord.isOutgoing -> "Outgoing"
      else -> "Incoming"
    }

    return MessageInfo(
      messageId = messageRecord.id,
      threadId = messageRecord.threadId,
      body = messageRecord.body,
      fromRecipientName = messageRecord.fromRecipient.getDisplayName(AppDependencies.application),
      fromRecipientId = messageRecord.fromRecipient.id.toLong(),
      toRecipientName = messageRecord.toRecipient.getDisplayName(AppDependencies.application),
      toRecipientId = messageRecord.toRecipient.id.toLong(),
      dateSent = messageRecord.dateSent,
      dateReceived = messageRecord.dateReceived,
      isOutgoing = messageRecord.isOutgoing,
      type = messageType
    ).toJsonResult()
  }

  data class LocalBackups @JsonCreator constructor(@field:JsonProperty val backups: List<LocalBackup>)

  data class LocalBackup @JsonCreator constructor(@field:JsonProperty val name: String, @field:JsonProperty val timestamp: Long)

  data class RecipientInfo @JsonCreator constructor(
    @field:JsonProperty val name: String,
    @field:JsonProperty val aci: String? = null,
    @field:JsonProperty val pni: String? = null,
    @field:JsonProperty val e164: String? = null,
    @field:JsonProperty val username: String? = null,
    @field:JsonProperty val nickname: String? = null,
    @field:JsonProperty val note: String? = null,
    @field:JsonProperty val about: String? = null,
    @field:JsonProperty val aboutEmoji: String? = null,
    @field:JsonProperty val isBlocked: Boolean = false,
    @field:JsonProperty val isSystemContact: Boolean = false,
    @field:JsonProperty val isGroup: Boolean = false,
    @field:JsonProperty val groupId: String? = null,
    @field:JsonProperty val groupType: String? = null,
    @field:JsonProperty val isActiveGroup: Boolean = false,
    @field:JsonProperty val participantCount: Int? = null
  )

  data class ThreadInfo @JsonCreator constructor(
    @field:JsonProperty val threadId: Long,
    @field:JsonProperty val recipientName: String,
    @field:JsonProperty val recipientId: Long,
    @field:JsonProperty val unreadCount: Int,
    @field:JsonProperty val snippet: String? = null,
    @field:JsonProperty val date: Long,
    @field:JsonProperty val archived: Boolean = false,
    @field:JsonProperty val pinned: Boolean = false,
    @field:JsonProperty val unreadSelfMentionsCount: Int = 0
  )

  data class MessageInfo @JsonCreator constructor(
    @field:JsonProperty val messageId: Long,
    @field:JsonProperty val threadId: Long,
    @field:JsonProperty val body: String? = null,
    @field:JsonProperty val fromRecipientName: String,
    @field:JsonProperty val fromRecipientId: Long,
    @field:JsonProperty val toRecipientName: String? = null,
    @field:JsonProperty val toRecipientId: Long? = null,
    @field:JsonProperty val dateSent: Long,
    @field:JsonProperty val dateReceived: Long,
    @field:JsonProperty val isOutgoing: Boolean,
    @field:JsonProperty val type: String
  )

  fun Any.toJsonResult(): PluginResult.JsonResult {
    return PluginResult.JsonResult(JsonUtil.toJson(this))
  }
}
