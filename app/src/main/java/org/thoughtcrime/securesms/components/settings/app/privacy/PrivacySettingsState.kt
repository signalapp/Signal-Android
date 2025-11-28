package org.thoughtcrime.securesms.components.settings.app.privacy

data class PrivacySettingsState(
  val blockedCount: Int,
  val readReceipts: Boolean,
  val typingIndicators: Boolean,
  val receiptDeliveryDelay: Boolean,
  val deliveryReceiptsForEdits: Boolean,
  val deliveryReceiptsForReactions: Boolean,
  val deliveryReceiptsForDeletes: Boolean,
  val screenLock: Boolean,
  val screenLockActivityTimeout: Long,
  val screenSecurity: Boolean,
  val incognitoKeyboard: Boolean,
  val paymentLock: Boolean,
  val isObsoletePasswordEnabled: Boolean,
  val isObsoletePasswordTimeoutEnabled: Boolean,
  val obsoletePasswordTimeout: Int,
  val universalExpireTimer: Int
)
