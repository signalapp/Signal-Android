package org.thoughtcrime.securesms.components.settings.app.subscription

class DonationExceptions {
  class SetupFailed(reason: Throwable) : Exception(reason)
  object TimedOutWaitingForTokenRedemption : Exception()
  object RedemptionFailed : Exception()
}
