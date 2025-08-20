/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Observe attachment deletions.
 */
fun DatabaseObserver.attachmentDeletions(): Flow<Unit> {
  return observe { registerAttachmentDeletedObserver(it) }
}

/**
 * Observe attachment updates.
 */
fun DatabaseObserver.attachmentUpdates(): Flow<Unit> {
  return observe { registerAttachmentUpdatedObserver(it) }
}

/**
 * Helper to register flow-ize database observer
 */
private fun DatabaseObserver.observe(registerObserver: DatabaseObserver.(listener: DatabaseObserver.Observer) -> Unit): Flow<Unit> {
  return callbackFlow {
    val listener = DatabaseObserver.Observer {
      trySend(Unit)
    }

    this@observe.registerObserver(listener)
    awaitClose {
      this@observe.unregisterObserver(listener)
    }
  }
}
