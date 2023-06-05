/**
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links

import io.reactivex.rxjava3.core.Observable
import org.signal.core.util.Hex
import org.signal.core.util.logging.Log
import org.signal.ringrtc.CallLinkRootKey
import org.thoughtcrime.securesms.database.CallLinkTable
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import java.net.URLDecoder

/**
 * Utility object for call links to try to keep some common logic in one place.
 */
object CallLinks {
  private const val ROOT_KEY = "key"
  private const val LINK_PREFIX = "https://signal.link/call/#key="

  private val TAG = Log.tag(CallLinks::class.java)

  fun url(linkKeyBytes: ByteArray) = "$LINK_PREFIX${Hex.dump(linkKeyBytes)}"

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

      ApplicationDependencies.getDatabaseObserver().registerCallLinkObserver(roomId, observer)
      emitter.setCancellable {
        ApplicationDependencies.getDatabaseObserver().unregisterObserver(observer)
      }

      refresh()
    }
  }

  @JvmStatic
  fun parseUrl(url: String): CallLinkRootKey? {
    if (!url.startsWith(LINK_PREFIX)) {
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

    // TODO Parse the key into a byte array
    return null
  }
}
