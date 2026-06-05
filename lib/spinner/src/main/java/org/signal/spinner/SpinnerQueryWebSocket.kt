/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.spinner

import android.annotation.SuppressLint
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocket
import org.signal.core.util.tracing.Tracer
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@SuppressLint("LogNotSignal")
internal class SpinnerQueryWebSocket(handshakeRequest: NanoHTTPD.IHTTPSession) : WebSocket(handshakeRequest) {

  companion object {
    private const val TAG = "SpinnerQueryWebSocket"

    private const val MAX_PENDING = 10_000

    private val pending: ArrayDeque<SpinnerQueryEvent> = ArrayDeque()
    private val openSockets: MutableList<SpinnerQueryWebSocket> = mutableListOf()

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    private val dispatchThread: DispatchThread = DispatchThread().also { it.start() }

    /** Per-track stack of currently-forwarded frame names. Used to drop end events for frames we filtered out. */
    private val forwardedFrames: ConcurrentHashMap<Long, ArrayDeque<String>> = ConcurrentHashMap()

    private val tracerListener = object : Tracer.EventListener {
      override fun onStart(name: String, trackId: Long, trackName: String, timestampNanos: Long, values: Map<String, String>?) {
        val query = values?.get("query")
        val isLockEvent = trackId == Tracer.TrackId.DB_LOCK
        if (query == null && !isLockEvent) {
          return
        }

        val stack = forwardedFrames.getOrPut(trackId) { ArrayDeque() }
        synchronized(stack) {
          stack.addLast(name)
        }

        enqueue(
          SpinnerQueryEvent.Start(
            trackId = trackId,
            trackName = trackName,
            name = name,
            query = query,
            table = values?.get("table"),
            holder = if (isLockEvent) values?.get("thread") else null,
            timestampMs = timestampNanos / 1_000_000L
          )
        )
      }

      override fun onEnd(name: String, trackId: Long, timestampNanos: Long) {
        val stack = forwardedFrames[trackId] ?: return
        val matched = synchronized(stack) {
          if (stack.isNotEmpty() && stack.last() == name) {
            stack.removeLast()
            true
          } else {
            false
          }
        }
        if (!matched) {
          return
        }

        enqueue(
          SpinnerQueryEvent.End(
            trackId = trackId,
            name = name,
            timestampMs = timestampNanos / 1_000_000L
          )
        )
      }
    }

    private fun enqueue(event: SpinnerQueryEvent) {
      lock.withLock {
        if (openSockets.isEmpty()) {
          return
        }
        pending += event
        if (pending.size > MAX_PENDING) {
          pending.removeFirst()
        }
        condition.signal()
      }
    }
  }

  override fun onOpen() {
    Log.d(TAG, "onOpen()")

    val firstSocket = lock.withLock {
      openSockets += this
      condition.signal()
      openSockets.size == 1
    }

    if (firstSocket) {
      Tracer.getInstance().addEventListener(tracerListener)
    }
  }

  override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode, reason: String?, initiatedByRemote: Boolean) {
    Log.d(TAG, "onClose()")

    val lastSocket = lock.withLock {
      openSockets -= this
      openSockets.isEmpty()
    }

    if (lastSocket) {
      Tracer.getInstance().removeEventListener(tracerListener)
      forwardedFrames.clear()
      lock.withLock {
        pending.clear()
      }
    }
  }

  override fun onMessage(message: NanoWSD.WebSocketFrame) = Unit

  override fun onPong(pong: NanoWSD.WebSocketFrame) = Unit

  override fun onException(exception: IOException) {
    Log.d(TAG, "onException()", exception)
  }

  private class DispatchThread : Thread("SpinnerQuery") {
    override fun run() {
      while (true) {
        val (sockets, event) = lock.withLock {
          while (pending.isEmpty() || openSockets.isEmpty()) {
            condition.await()
          }
          openSockets.toList() to pending.removeFirst()
        }

        val payload = event.serialize()
        sockets.forEach { socket ->
          try {
            socket.send(payload)
          } catch (e: IOException) {
            Log.w(TAG, "Failed to send a query event!", e)
          }
        }
      }
    }
  }
}
