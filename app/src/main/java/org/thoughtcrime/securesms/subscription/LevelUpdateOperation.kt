package org.thoughtcrime.securesms.subscription

import org.whispersystems.signalservice.api.subscriptions.IdempotencyKey
import java.io.Closeable

/**
 * Binds a Subscription level update with an idempotency key.
 *
 * We are to use the same idempotency key whenever we want to retry updating to a particular level.
 */
data class LevelUpdateOperation(
  val idempotencyKey: IdempotencyKey,
  val level: String
) : Closeable {
  override fun close() {
    LevelUpdate.updateProcessingState(false)
  }
}
