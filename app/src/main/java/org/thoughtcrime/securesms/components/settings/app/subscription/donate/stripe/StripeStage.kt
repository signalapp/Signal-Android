package org.thoughtcrime.securesms.components.settings.app.subscription.donate.stripe

enum class StripeStage {
  INIT,
  PAYMENT_PIPELINE,
  CANCELLING,
  FAILED,
  COMPLETE
}
