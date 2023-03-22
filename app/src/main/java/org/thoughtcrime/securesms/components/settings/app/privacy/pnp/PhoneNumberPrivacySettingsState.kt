package org.thoughtcrime.securesms.components.settings.app.privacy.pnp

import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues

data class PhoneNumberPrivacySettingsState(
  val seeMyPhoneNumber: PhoneNumberPrivacyValues.PhoneNumberSharingMode,
  val findMeByPhoneNumber: PhoneNumberPrivacyValues.PhoneNumberListingMode
)
