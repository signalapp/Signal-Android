/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.svr

import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.internal.push.PushServiceSocket

class SvrApi(private val pushServiceSocket: PushServiceSocket) {

  companion object {
    @JvmStatic
    fun create(pushServiceSocket: PushServiceSocket): SvrApi {
      return SvrApi(pushServiceSocket)
    }
  }

  /**
   * Store the latest share-set on the service. The share-set is a piece of data generated in the course of setting a PIN on SVR3 that needs to be
   */
  fun setShareSet(shareSet: ByteArray): NetworkResult<Unit> {
    return NetworkResult.fromFetch {
      pushServiceSocket.setShareSet(shareSet)
    }
  }
}
