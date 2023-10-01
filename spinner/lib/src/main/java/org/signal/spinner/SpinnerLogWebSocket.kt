/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.spinner

import android.annotation.SuppressLint
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocket
import java.io.IOException
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@SuppressLint("LogNotSignal")
internal class SpinnerLogWebSocket(handshakeRequest: NanoHTTPD.IHTTPSession) : WebSocket(handshakeRequest) {

  companion object {
    private val TAG = "SpinnerLogWebSocket"

    private const val MAX_LOGS = 5_000

    private val logs: Queue<SpinnerLogItem> = LinkedList()
    private val openSockets: MutableList<SpinnerLogWebSocket> = mutableListOf()

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    private val logThread: LogThread = LogThread().also { it.start() }

    fun onLog(item: SpinnerLogItem) {
      lock.withLock {
        logs += item
        if (logs.size > MAX_LOGS) {
          logs.remove()
        }
        condition.signal()
      }
    }
  }

  override fun onOpen() {
    Log.d(TAG, "onOpen()")

    lock.withLock {
      openSockets += this
      condition.signal()
    }
  }

  override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode, reason: String?, initiatedByRemote: Boolean) {
    Log.d(TAG, "onClose()")

    lock.withLock {
      openSockets -= this
    }
  }

  override fun onMessage(message: NanoWSD.WebSocketFrame) {
    Log.d(TAG, "onMessage()")
  }

  override fun onPong(pong: NanoWSD.WebSocketFrame) {
    Log.d(TAG, "onPong()")
  }

  override fun onException(exception: IOException) {
    Log.d(TAG, "onException()", exception)
  }

  private class LogThread : Thread("SpinnerLog") {
    override fun run() {
      while (true) {
        val (sockets, log) = lock.withLock {
          while (logs.isEmpty() || openSockets.isEmpty()) {
            condition.await()
          }

          openSockets.toList() to logs.remove()
        }

        sockets.forEach { socket ->
          try {
            socket.send(log.serialize())
          } catch (e: IOException) {
            Log.w(TAG, "Failed to send a log to the socket!", e)
          }
        }
      }
    }
  }
}
