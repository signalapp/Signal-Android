package org.thoughtcrime.securesms.components.settings.app.privacy

import org.thoughtcrime.securesms.database.model.DistributionListPartialRecord
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues

data class PrivacySettingsState(
  val blockedCount: Int,
  val seeMyPhoneNumber: PhoneNumberPrivacyValues.PhoneNumberSharingMode,
  val findMeByPhoneNumber: PhoneNumberPrivacyValues.PhoneNumberListingMode,
  val readReceipts: Boolean,
  val typingIndicators: Boolean,
  val screenLock: Boolean,
  val screenLockActivityTimeout: Long,
  val screenSecurity: Boolean,
  val incognitoKeyboard: Boolean,
  val isObsoletePasswordEnabled: Boolean,
  val isObsoletePasswordTimeoutEnabled: Boolean,
  val obsoletePasswordTimeout: Int,
  val universalExpireTimer: Int,
  val privateStories: List<DistributionListPartialRecord>,
  val isStoriesEnabled: Boolean
)
