package org.thoughtcrime.securesms.components.settings.app.subscription

class DonationExceptions {
  object TimedOutWaitingForTokenRedemption : Exception()
  object RedemptionFailed : Exception()
}
