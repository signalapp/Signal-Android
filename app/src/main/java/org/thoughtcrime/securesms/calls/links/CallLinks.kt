/**
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links

import io.reactivex.rxjava3.core.Observable
import org.signal.core.util.logging.Log
import org.signal.ringrtc.CallException
import org.signal.ringrtc.CallLinkRootKey
import org.thoughtcrime.securesms.database.CallLinkTable
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import java.net.URLDecoder

/**
 * Utility object for call links to try to keep some common logic in one place.
 */
object CallLinks {
  private const val ROOT_KEY = "key"
  private const val HTTPS_LINK_PREFIX = "https://signal.link/call/#key="
  private const val SNGL_LINK_PREFIX = "sgnl://signal.link/call/#key="

  private val TAG = Log.tag(CallLinks::class.java)

  fun url(linkKeyBytes: ByteArray) = "$HTTPS_LINK_PREFIX${CallLinkRootKey(linkKeyBytes)}"

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

  @JvmStatic
  fun parseUrl(url: String): CallLinkRootKey? {
    if (!url.startsWith(HTTPS_LINK_PREFIX) && !url.startsWith(SNGL_LINK_PREFIX)) {
      Log.w(TAG, "Invalid url prefix.")
      return null
    }

    val parts = url.split("#")
    if (parts.size != 2) {
      Log.w(TAG, "Invalid fragment delimiter count in url.")
      return null
    }

    val fragment = parts[1]
    val fragmentParts = fragment.split("&")
    val fragmentQuery = fragmentParts.associate {
      val kv = it.split("=")
      if (kv.size != 2) {
        Log.w(TAG, "Invalid fragment keypair. Skipping.")
      }

      val key = URLDecoder.decode(kv[0], "utf8")
      val value = URLDecoder.decode(kv[1], "utf8")

      key to value
    }

    val key = fragmentQuery[ROOT_KEY]
    if (key == null) {
      Log.w(TAG, "Root key not found in fragment query string.")
      return null
    }

    return try {
      CallLinkRootKey(key)
    } catch (e: CallException) {
      Log.w(TAG, "Invalid root key found in fragment query string.")
      null
    }
  }
}
