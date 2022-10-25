package org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe

enum class StripeStage {
  INIT,
  PAYMENT_PIPELINE,
  CANCELLING,
  FAILED,
  COMPLETE;

  val isInProgress: Boolean get() = this == PAYMENT_PIPELINE || this == CANCELLING
  val isTerminal: Boolean get() = this == FAILED || this == COMPLETE
}
