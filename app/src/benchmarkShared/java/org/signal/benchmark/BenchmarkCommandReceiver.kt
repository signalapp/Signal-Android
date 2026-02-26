/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.signal.benchmark.setup.Generator
import org.signal.benchmark.setup.Harness
import org.signal.benchmark.setup.OtherClient
import org.signal.core.util.ThreadUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.TestDbUtils
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.internal.push.Envelope
import org.whispersystems.signalservice.internal.websocket.BenchmarkWebSocketConnection
import org.whispersystems.signalservice.internal.websocket.WebSocketRequestMessage
import kotlin.random.Random

/**
 * A BroadcastReceiver that accepts commands sent from the benchmark app to perform
 * background operations on the client.
 */
class BenchmarkCommandReceiver : BroadcastReceiver() {

  companion object {
    private val TAG = Log.tag(BenchmarkCommandReceiver::class)

    const val ACTION_COMMAND = "org.signal.benchmark.action.COMMAND"
    const val EXTRA_COMMAND = "command"
  }

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != ACTION_COMMAND) {
      Log.w(TAG, "Ignoring unknown action: ${intent.action}")
      return
    }

    val command = intent.getStringExtra(EXTRA_COMMAND)
    Log.i(TAG, "Received command: $command")

    when (command) {
      "individual-send" -> handlePrepareIndividualSend()
      "group-send" -> handlePrepareGroupSend()
      "group-delivery-receipt" -> handlePrepareGroupReceipts { client, timestamps -> client.generateInboundDeliveryReceipts(timestamps) }
      "group-read-receipt" -> handlePrepareGroupReceipts { client, timestamps -> client.generateInboundReadReceipts(timestamps) }
      "release-messages" -> {
        BenchmarkWebSocketConnection.authInstance.startWholeBatchTrace = true
        BenchmarkWebSocketConnection.authInstance.releaseMessages()
      }
      else -> Log.w(TAG, "Unknown command: $command")
    }
  }

  private fun handlePrepareIndividualSend() {
    val client = Harness.otherClients[0]

    // Send message from Bob to Self
    val encryptedEnvelope = client.encrypt(Generator.encryptedTextMessage(System.currentTimeMillis()))

    runBlocking {
      launch(Dispatchers.IO) {
        BenchmarkWebSocketConnection.authInstance.run {
          Log.i(TAG, "Sending initial message form Bob to establish session.")
          addPendingMessages(listOf(encryptedEnvelope.toWebSocketPayload()))
          releaseMessages()

          // Sleep briefly to let the message be processed.
          ThreadUtil.sleep(100)
        }
      }
    }

    // Have Bob generate N messages that will be received by Alice
    val messageCount = 100
    val envelopes = client.generateInboundEnvelopes(messageCount)

    val messages = envelopes.map { e -> e.toWebSocketPayload() }

    BenchmarkWebSocketConnection.authInstance.addPendingMessages(messages)
    BenchmarkWebSocketConnection.authInstance.addQueueEmptyMessage()
  }

  private fun handlePrepareGroupSend() {
    val clients = Harness.otherClients.take(5)

    // Send message from others to Self in the group
    val encryptedEnvelopes = clients.map { it.encrypt(Generator.encryptedTextMessage(System.currentTimeMillis(), groupMasterKey = Harness.groupMasterKey)) }

    runBlocking {
      launch(Dispatchers.IO) {
        BenchmarkWebSocketConnection.authInstance.run {
          Log.i(TAG, "Sending initial group messages from client to establish sessions.")
          addPendingMessages(encryptedEnvelopes.map { it.toWebSocketPayload() })
          releaseMessages()

          // Sleep briefly to let the messages be processed.
          ThreadUtil.sleep(1000)
        }
      }
    }

    // Have clients generate N group messages that will be received by Alice
    clients.forEach { client ->
      val messageCount = 100
      val envelopes = client.generateInboundGroupEnvelopes(messageCount, Harness.groupMasterKey)

      val messages = envelopes.map { e -> e.toWebSocketPayload() }

      BenchmarkWebSocketConnection.authInstance.addPendingMessages(messages)
    }
    BenchmarkWebSocketConnection.authInstance.addQueueEmptyMessage()
  }

  private fun handlePrepareGroupReceipts(generateReceipts: (OtherClient, List<Long>) -> List<Envelope>) {
    val clients = Harness.otherClients.take(5)

    establishGroupSessions(clients)

    val timestamps = getOutgoingGroupMessageTimestamps()
    Log.i(TAG, "Found ${timestamps.size} outgoing message timestamps for receipts")

    val allClientEnvelopes = clients.map { client ->
      generateReceipts(client, timestamps).map { it.toWebSocketPayload() }
    }

    BenchmarkWebSocketConnection.authInstance.addPendingMessages(interleave(allClientEnvelopes))
    BenchmarkWebSocketConnection.authInstance.addQueueEmptyMessage()
  }

  private fun establishGroupSessions(clients: List<OtherClient>) {
    val encryptedEnvelopes = clients.map { it.encrypt(Generator.encryptedTextMessage(System.currentTimeMillis(), groupMasterKey = Harness.groupMasterKey)) }

    runBlocking {
      launch(Dispatchers.IO) {
        BenchmarkWebSocketConnection.authInstance.run {
          Log.i(TAG, "Sending initial group messages from clients to establish sessions.")
          addPendingMessages(encryptedEnvelopes.map { it.toWebSocketPayload() })
          releaseMessages()
          ThreadUtil.sleep(1000)
        }
      }
    }
  }

  private fun getOutgoingGroupMessageTimestamps(): List<Long> {
    val groupId = GroupId.v2(Harness.groupMasterKey)
    val groupRecipient = Recipient.externalGroupExact(groupId)
    val threadId = SignalDatabase.threads.getOrCreateThreadIdFor(groupRecipient)
    val selfId = Recipient.self().id.toLong()
    return TestDbUtils.getOutgoingMessageTimestamps(threadId, selfId)
  }

  /**
   * Interleaves lists so that items from different lists alternate:
   * [[a1, a2], [b1, b2], [c1, c2]] -> [a1, b1, c1, a2, b2, c2]
   */
  private fun <T> interleave(lists: List<List<T>>): List<T> {
    val result = mutableListOf<T>()
    val maxSize = lists.maxOf { it.size }
    for (i in 0 until maxSize) {
      for (list in lists) {
        if (i < list.size) {
          result += list[i]
        }
      }
    }
    return result
  }

  private fun Envelope.toWebSocketPayload(): WebSocketRequestMessage {
    return WebSocketRequestMessage(
      verb = "PUT",
      path = "/api/v1/message",
      id = Random.nextLong(),
      headers = listOf("X-Signal-Timestamp: ${this.timestamp}"),
      body = this.encodeByteString()
    )
  }
}
