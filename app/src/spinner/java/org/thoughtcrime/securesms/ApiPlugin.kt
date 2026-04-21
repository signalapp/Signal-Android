/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.signal.core.models.ServiceId
import org.signal.core.util.Hex
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.spinner.Plugin
import org.signal.spinner.PluginResult
import org.signal.storageservice.storage.protos.groups.Member
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroup
import org.signal.storageservice.storage.protos.groups.local.DecryptedMember
import org.signal.storageservice.storage.protos.groups.local.DecryptedPendingMember
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.database.JobDatabase
import org.thoughtcrime.securesms.database.KeyValueDatabase
import org.thoughtcrime.securesms.database.LocalMetricsDatabase
import org.thoughtcrime.securesms.database.LogDatabase
import org.thoughtcrime.securesms.database.MegaphoneDatabase
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import org.whispersystems.signalservice.internal.util.JsonUtil

class ApiPlugin : Plugin {
  companion object {
    private val TAG = Log.tag(ApiPlugin::class.java)
    const val PATH = "/api"
  }

  override val name: String = "APIs"
  override val path: String = PATH

  private val apis: Map<String, ApiSpec> = mapOf(
    "localBackups" to ApiSpec(::localBackups, listOf(Param("cacheBust", "false"))),
    "recipient" to ApiSpec(::recipient, listOf(Param("id", "1"))),
    "thread" to ApiSpec(::thread, listOf(Param("id", "1"))),
    "message" to ApiSpec(::message, listOf(Param("id", "1"))),
    "query" to ApiSpec(
      ::query,
      listOf(
        Param("sql", "", placeholder = "SELECT ..."),
        Param("db", "signal", placeholder = "signal|keyvalue|jobmanager|megaphones|localmetrics|logs")
      )
    ),
    "createRecipient" to ApiSpec(
      ::createRecipient,
      listOf(
        Param("aci", "", placeholder = "blank = random UUID"),
        Param("e164", "", placeholder = "+15551234567"),
        Param("givenName", "Test"),
        Param("familyName", "User"),
        Param("nicknameGiven", ""),
        Param("nicknameFamily", ""),
        Param("note", ""),
        Param("systemContactName", ""),
        Param("username", ""),
        Param("profileSharing", "true"),
        Param("blocked", "false"),
        Param("hidden", "false"),
        Param("registered", "true")
      )
    ),
    "createGroup" to ApiSpec(
      ::createGroup,
      listOf(
        Param("title", "Test Group"),
        Param("description", ""),
        Param("memberAcis", "", placeholder = "comma-separated ACIs"),
        Param("pendingAcis", "", placeholder = "comma-separated ACIs"),
        Param("blocked", "false"),
        Param("profileSharing", "false")
      )
    ),
    "createThread" to ApiSpec(::createThread, listOf(Param("recipientId", "1"))),
    "createMessage" to ApiSpec(
      ::createMessage,
      listOf(
        Param("threadId", "1"),
        Param("fromRecipientId", "1"),
        Param("toRecipientId", "1"),
        Param("body", "Hello"),
        Param("outgoing", "false")
      )
    )
  )

  override fun get(parameters: Map<String, List<String>>): PluginResult {
    val api = parameters["api"]?.firstOrNull()

    if (api == null) {
      val apiSections = apis.entries.joinToString("\n") { (apiName, spec) ->
        val inputs = spec.params.joinToString("\n") { p ->
          val placeholder = p.placeholder?.let { " placeholder=\"${escape(it)}\"" } ?: ""
          """
            <label style="display: inline-block; margin-right: 10px;">
              ${p.name}:
              <input type="text" data-api="$apiName" data-parameter="${p.name}" value="${escape(p.default)}"$placeholder style="padding: 4px;" />
            </label>
          """.trimIndent()
        }
        """
          <div style="margin-bottom: 24px; padding: 10px; border: 1px solid #ccc;">
            <div style="font-weight: bold; margin-bottom: 6px;">$apiName</div>
            <div style="margin-bottom: 8px;">$inputs</div>
            <button id="button_$apiName" style="padding: 8px 14px; margin-right: 10px;">Call</button>
            <pre id="result_$apiName" style="display: inline-block; vertical-align: top; margin: 0; white-space: pre-wrap; font-weight: bold;"></pre>
          </div>
        """.trimIndent()
      }

      val apiScripts = apis.keys.joinToString("\n") { apiName ->
        """
          document.getElementById('button_$apiName').addEventListener('click', async function() {
            const resultElement = document.getElementById('result_$apiName');
            resultElement.textContent = 'Loading...';

            const inputElements = document.querySelectorAll('input[data-api="$apiName"]');
            const queryParameters = new URLSearchParams();
            queryParameters.append('api', '$apiName');
            inputElements.forEach(function(inputElement) {
              if (inputElement.value !== '') {
                queryParameters.append(inputElement.dataset.parameter, inputElement.value);
              }
            });

            try {
              const response = await fetch('$PATH?' + queryParameters.toString());
              const responseText = await response.text();
              if (!response.ok) {
                resultElement.textContent = 'Error: ' + responseText;
                return;
              }
              try {
                resultElement.textContent = JSON.stringify(JSON.parse(responseText), null, 2);
              } catch (parseError) {
                resultElement.textContent = responseText;
              }
            } catch (fetchError) {
              resultElement.textContent = 'Error: ' + fetchError.message;
            }
          });
        """.trimIndent()
      }

      val html = """
        <h3>Available APIs</h3>
        $apiSections

        <script>
          $apiScripts
        </script>
      """.trimIndent()

      return PluginResult.RawHtmlResult(html)
    }

    return apis[api]?.handler?.invoke(parameters) ?: PluginResult.ErrorResult.notFound(message = "not found")
  }

  private fun escape(value: String): String {
    return value
      .replace("&", "&amp;")
      .replace("\"", "&quot;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
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

  private fun query(parameters: Map<String, List<String>>): PluginResult {
    val sql = parameters["sql"]?.firstOrNull() ?: return PluginResult.ErrorResult(message = "Missing 'sql' parameter")
    val dbName = parameters["db"]?.firstOrNull() ?: "signal"

    return try {
      val db = when (dbName) {
        "signal" -> SignalDatabase.rawDatabase
        "keyvalue" -> KeyValueDatabase.getInstance(AppDependencies.application).sqlCipherDatabase
        "jobmanager" -> JobDatabase.getInstance(AppDependencies.application).sqlCipherDatabase
        "megaphones" -> MegaphoneDatabase.getInstance(AppDependencies.application).sqlCipherDatabase
        "localmetrics" -> LocalMetricsDatabase.getInstance(AppDependencies.application).sqlCipherDatabase
        "logs" -> LogDatabase.getInstance(AppDependencies.application).sqlCipherDatabase
        else -> return PluginResult.ErrorResult(message = "Unknown database: $dbName. Available: signal, keyvalue, jobmanager, megaphones, localmetrics, logs")
      }
      val cursor = db.rawQuery(sql, null)
      val columns = (0 until cursor.columnCount).map { cursor.getColumnName(it) }

      val rows = object : Iterator<List<String?>>, AutoCloseable {
        private var advanced = false
        private var hasMore = false

        override fun hasNext(): Boolean {
          if (!advanced) {
            hasMore = cursor.moveToNext()
            advanced = true
            if (!hasMore) cursor.close()
          }
          return hasMore
        }

        override fun next(): List<String?> {
          if (!hasNext()) throw NoSuchElementException()
          advanced = false
          return (0 until cursor.columnCount).map { i ->
            when {
              cursor.isNull(i) -> null
              cursor.getType(i) == android.database.Cursor.FIELD_TYPE_BLOB -> {
                val bytes = cursor.getBlob(i)
                "<BLOB ${bytes.size} bytes: ${Hex.toStringCondensed(bytes)}>"
              }
              else -> cursor.getString(i)
            }
          }
        }

        override fun close() {
          cursor.close()
        }
      }

      PluginResult.TsvResult(columns, rows)
    } catch (e: Exception) {
      PluginResult.ErrorResult(message = "Query failed: ${e.message}")
    }
  }

  private fun createRecipient(parameters: Map<String, List<String>>): PluginResult {
    val aciStr = parameters["aci"]?.firstOrNull() ?: java.util.UUID.randomUUID().toString()
    val e164 = parameters["e164"]?.firstOrNull()
    val givenName = parameters["givenName"]?.firstOrNull()
    val familyName = parameters["familyName"]?.firstOrNull()
    val nicknameGiven = parameters["nicknameGiven"]?.firstOrNull()
    val nicknameFamily = parameters["nicknameFamily"]?.firstOrNull()
    val note = parameters["note"]?.firstOrNull()
    val systemContactName = parameters["systemContactName"]?.firstOrNull()
    val username = parameters["username"]?.firstOrNull()
    val profileSharing = parameters.boolOrDefault("profileSharing", true)
    val blocked = parameters.boolOrDefault("blocked", false)
    val hidden = parameters.boolOrDefault("hidden", false)
    val registered = parameters.boolOrDefault("registered", true)

    return try {
      val aci = ServiceId.ACI.parseOrThrow(aciStr)
      val recipientId = if (e164 != null) {
        SignalDatabase.recipients.getAndPossiblyMerge(aci = aci, pni = null, e164 = e164)
      } else {
        SignalDatabase.recipients.getOrInsertFromServiceId(aci)
      }

      if (givenName != null || familyName != null) {
        SignalDatabase.recipients.setProfileName(recipientId, ProfileName.fromParts(givenName, familyName))
      }

      SignalDatabase.recipients.setProfileKeyIfAbsent(recipientId, ProfileKeyUtil.createNew())
      SignalDatabase.recipients.setCapabilities(recipientId, SignalServiceProfile.Capabilities(true, true))
      SignalDatabase.recipients.setProfileSharing(recipientId, profileSharing)

      if (registered) {
        SignalDatabase.recipients.markRegistered(recipientId, aci)
      }

      if (nicknameGiven != null || nicknameFamily != null || note != null) {
        SignalDatabase.recipients.setNicknameAndNote(
          recipientId,
          ProfileName.fromParts(nicknameGiven, nicknameFamily),
          note ?: ""
        )
      }

      if (systemContactName != null) {
        SignalDatabase.recipients.setSystemContactName(recipientId, systemContactName)
      }

      if (username != null) {
        SignalDatabase.recipients.setUsername(recipientId, username)
      }

      if (blocked) {
        SignalDatabase.recipients.setBlocked(recipientId, true)
      }

      if (hidden) {
        SignalDatabase.recipients.markHidden(recipientId)
      }

      CreateRecipientResponse(recipientId.toLong(), aciStr).toJsonResult()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to create recipient", e)
      PluginResult.ErrorResult(message = "Failed: ${e.message}")
    }
  }

  private fun createGroup(parameters: Map<String, List<String>>): PluginResult {
    val title = parameters["title"]?.firstOrNull() ?: return PluginResult.ErrorResult(message = "Missing 'title' parameter")
    val description = parameters["description"]?.firstOrNull()
    val memberAcis = parameters["memberAcis"]?.firstOrNull()?.split(",")?.map { it.trim() } ?: emptyList()
    val pendingAcis = parameters["pendingAcis"]?.firstOrNull()?.split(",")?.map { it.trim() } ?: emptyList()
    val blocked = parameters.boolOrDefault("blocked", false)
    val profileSharing = parameters.boolOrDefault("profileSharing", false)

    return try {
      val masterKeyBytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
      val groupMasterKey = GroupMasterKey(masterKeyBytes)

      memberAcis.forEach { SignalDatabase.recipients.getOrInsertFromServiceId(ServiceId.ACI.parseOrThrow(it)) }

      val members = memberAcis.map { aciStr ->
        val aci = ServiceId.ACI.parseOrThrow(aciStr)
        DecryptedMember(
          aciBytes = aci.toByteString(),
          role = Member.Role.DEFAULT,
          profileKey = okio.ByteString.of(*ByteArray(32)),
          joinedAtRevision = 0
        )
      }

      val pendingMembers = pendingAcis.map { aciStr ->
        val aci = ServiceId.ACI.parseOrThrow(aciStr)
        DecryptedPendingMember(
          serviceIdBytes = aci.toByteString(),
          role = Member.Role.DEFAULT,
          addedByAci = if (members.isNotEmpty()) members.first().aciBytes else okio.ByteString.of(*ByteArray(16)),
          timestamp = System.currentTimeMillis()
        )
      }

      val groupState = DecryptedGroup(
        title = title,
        description = description ?: "",
        revision = 1,
        members = members,
        pendingMembers = pendingMembers
      )

      val groupId = SignalDatabase.groups.create(groupMasterKey, groupState, groupSendEndorsements = null)
        ?: return PluginResult.ErrorResult(message = "Failed to create group, may already exist")

      val groupRecipientId = SignalDatabase.recipients.getOrInsertFromGroupId(groupId)

      if (blocked) {
        SignalDatabase.recipients.setBlocked(groupRecipientId, true)
      }
      SignalDatabase.recipients.setProfileSharing(groupRecipientId, profileSharing)

      CreateGroupResponse(
        recipientId = groupRecipientId.toLong(),
        groupId = groupId.toString()
      ).toJsonResult()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to create group", e)
      PluginResult.ErrorResult(message = "Failed: ${e.message}")
    }
  }

  private fun createThread(parameters: Map<String, List<String>>): PluginResult {
    val recipientIdLong = parameters["recipientId"]?.firstOrNull()?.toLongOrNull()
      ?: return PluginResult.ErrorResult(message = "Missing or invalid 'recipientId' parameter")

    return try {
      val recipientId = RecipientId.from(recipientIdLong)
      val recipient = Recipient.resolved(recipientId)
      val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(recipientId, isGroup = recipient.isGroup)
      CreateThreadResponse(threadId).toJsonResult()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to create thread", e)
      PluginResult.ErrorResult(message = "Failed: ${e.message}")
    }
  }

  private fun createMessage(parameters: Map<String, List<String>>): PluginResult {
    val threadId = parameters["threadId"]?.firstOrNull()?.toLongOrNull()
      ?: return PluginResult.ErrorResult(message = "Missing or invalid 'threadId' parameter")
    val fromRecipientId = parameters["fromRecipientId"]?.firstOrNull()?.toLongOrNull()
      ?: return PluginResult.ErrorResult(message = "Missing or invalid 'fromRecipientId' parameter")
    val toRecipientId = parameters["toRecipientId"]?.firstOrNull()?.toLongOrNull()
      ?: return PluginResult.ErrorResult(message = "Missing or invalid 'toRecipientId' parameter")
    val body = parameters["body"]?.firstOrNull() ?: ""
    val outgoing = parameters.boolOrDefault("outgoing", false)

    return try {
      val now = System.currentTimeMillis()
      if (outgoing) {
        val threadRecipient = Recipient.resolved(RecipientId.from(toRecipientId))
        val outgoingMessage = OutgoingMessage.text(
          threadRecipient = threadRecipient,
          body = body,
          expiresIn = 0,
          sentTimeMillis = now
        )
        val result = SignalDatabase.messages.insertMessageOutbox(outgoingMessage, threadId)
        CreateMessageResponse(result.messageId).toJsonResult()
      } else {
        val incomingMessage = IncomingMessage(
          type = MessageType.NORMAL,
          from = RecipientId.from(fromRecipientId),
          sentTimeMillis = now,
          serverTimeMillis = now,
          receivedTimeMillis = now,
          body = body
        )
        val inserted = SignalDatabase.messages.insertMessageInbox(incomingMessage, candidateThreadId = threadId)
        if (inserted.isPresent) {
          CreateMessageResponse(inserted.get().messageId).toJsonResult()
        } else {
          PluginResult.ErrorResult(message = "Incoming message was not inserted (likely a duplicate or filtered)")
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to create message", e)
      PluginResult.ErrorResult(message = "Failed: ${e.message}")
    }
  }

  private data class ApiSpec(
    val handler: (Map<String, List<String>>) -> PluginResult,
    val params: List<Param>
  )

  private data class Param(
    val name: String,
    val default: String,
    val placeholder: String? = null
  )

  data class CreateRecipientResponse @JsonCreator constructor(
    @field:JsonProperty val recipientId: Long,
    @field:JsonProperty val aci: String
  )

  data class CreateGroupResponse @JsonCreator constructor(
    @field:JsonProperty val recipientId: Long,
    @field:JsonProperty val groupId: String
  )

  data class CreateThreadResponse @JsonCreator constructor(
    @field:JsonProperty val threadId: Long
  )

  data class CreateMessageResponse @JsonCreator constructor(
    @field:JsonProperty val messageId: Long
  )

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

  private fun Map<String, List<String>>.boolOrDefault(key: String, default: Boolean): Boolean {
    return this[key]?.firstOrNull()?.toBooleanStrictOrNull() ?: default
  }
}
