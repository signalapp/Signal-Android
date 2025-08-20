/**
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links

import io.reactivex.rxjava3.core.Observable
import org.signal.core.util.logging.Log
import org.signal.ringrtc.CallException
import org.signal.ringrtc.CallLinkEpoch
import org.signal.ringrtc.CallLinkRootKey
import org.thoughtcrime.securesms.database.CallLinkTable
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import java.io.UnsupportedEncodingException
import java.net.URLDecoder

/**
 * Utility object for call links to try to keep some common logic in one place.
 */
object CallLinks {
  private const val ROOT_KEY = "key"
  private const val EPOCH = "epoch"
  private const val HTTPS_LINK_PREFIX = "https://signal.link/call/#key="
  private const val SNGL_LINK_PREFIX = "sgnl://signal.link/call/#key="

  private val TAG = Log.tag(CallLinks::class.java)

  fun url(rootKeyBytes: ByteArray, epochBytes: ByteArray?): String {
    return if (epochBytes == null) {
      "$HTTPS_LINK_PREFIX${CallLinkRootKey(rootKeyBytes)}"
    } else {
      "$HTTPS_LINK_PREFIX${CallLinkRootKey(rootKeyBytes)}&epoch=${CallLinkEpoch.fromBytes(epochBytes)}"
    }
  }

  fun watchCallLink(roomId: CallLinkRoomId): Observable<CallLinkTable.CallLink> {
    return Observable.create { emitter ->

      fun refresh() {
        val callLink = SignalDatabase.callLinks.getCallLinkByRoomId(roomId)
        if (callLink != null) {
          emitter.onNext(callLink)
        }
      }

      val observer = DatabaseObserver.Observer {
        refresh()
      }

      AppDependencies.databaseObserver.registerCallLinkObserver(roomId, observer)
      emitter.setCancellable {
        AppDependencies.databaseObserver.unregisterObserver(observer)
      }

      refresh()
    }
  }

  @JvmStatic
  fun isCallLink(url: String): Boolean {
    if (!url.startsWith(HTTPS_LINK_PREFIX) && !url.startsWith(SNGL_LINK_PREFIX)) {
      return false
    }

    return url.split("#").last().startsWith("key=")
  }

  data class CallLinkParseResult(
    val rootKey: CallLinkRootKey,
    val epoch: CallLinkEpoch?
  )

  @JvmStatic
  fun parseUrl(url: String): CallLinkParseResult? {
    if (!url.startsWith(HTTPS_LINK_PREFIX) && !url.startsWith(SNGL_LINK_PREFIX)) {
      Log.w(TAG, "Invalid url prefix.")
      return null
    }

    val parts = url.split("#")
    if (parts.size != 2) {
      Log.w(TAG, "Invalid fragment delimiter count in url.")
      return null
    }

    val fragmentQuery = mutableMapOf<String, String?>()

    try {
      for (part in parts[1].split("&")) {
        val kv = part.split("=")
        // Make sure we don't have an empty key (i.e. handle the case
        // of "a=0&&b=0", for example)
        if (kv[0].isEmpty()) {
          Log.w(TAG, "Invalid url: $url (empty key)")
          return null
        }
        val key = URLDecoder.decode(kv[0], "utf8")
        val value = when (kv.size) {
          1 -> null
          2 -> URLDecoder.decode(kv[1], "utf8")
          else -> {
            // Cannot have more than one value per key (i.e. handle the case
            // of "a=0&b=0=1=2", for example.
            Log.w(TAG, "Invalid url: $url (multiple values)")
            return null
          }
        }
        fragmentQuery += key to value
      }
    } catch (_: UnsupportedEncodingException) {
      Log.w(TAG, "Invalid url: $url")
      return null
    }

    val key = fragmentQuery[ROOT_KEY]
    if (key == null) {
      Log.w(TAG, "Root key not found in fragment query string.")
      return null
    }

    return try {
      val epoch = fragmentQuery[EPOCH]?.let { s -> CallLinkEpoch(s) }
      CallLinkParseResult(
        rootKey = CallLinkRootKey(key),
        epoch = epoch
      )
    } catch (e: CallException) {
      Log.w(TAG, "Invalid root key or epoch found in fragment query string.")
      null
    }
  }
}
