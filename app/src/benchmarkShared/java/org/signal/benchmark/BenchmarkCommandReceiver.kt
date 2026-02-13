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
import org.signal.benchmark.network.BenchmarkWebSocketConnection
import org.signal.benchmark.setup.Harness
import org.signal.core.util.ThreadUtil
import org.signal.core.util.logging.Log
import org.whispersystems.signalservice.internal.push.Envelope
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
      "release-messages" -> BenchmarkWebSocketConnection.instance.releaseMessages()
      else -> Log.w(TAG, "Unknown command: $command")
    }
  }

  private fun handlePrepareIndividualSend() {
    val bobClient = Harness.bobClient

    // Send message from Bob to Self
    val firstPreKeyMessageTimestamp = System.currentTimeMillis()
    val encryptedEnvelope = bobClient.encrypt(firstPreKeyMessageTimestamp)

    runBlocking {
      launch(Dispatchers.IO) {
        BenchmarkWebSocketConnection.instance.run {
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
    val envelopes = bobClient.generateInboundEnvelopes(messageCount)

    val messages = envelopes.map { e -> e.toWebSocketPayload() }

    BenchmarkWebSocketConnection.instance.addPendingMessages(messages)
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
