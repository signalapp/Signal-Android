package org.thoughtcrime.securesms.subscription

import org.whispersystems.signalservice.api.subscriptions.IdempotencyKey

/**
 * Binds a Subscription level update with an idempotency key.
 *
 * We are to use the same idempotency key whenever we want to retry updating to a particular level.
 */
data class LevelUpdateOperation(
  val idempotencyKey: IdempotencyKey,
  val level: String
)
