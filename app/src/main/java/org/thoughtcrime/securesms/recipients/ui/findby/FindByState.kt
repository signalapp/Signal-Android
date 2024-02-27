/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.findby

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.signal.core.util.orNull
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.util.CountryPrefix

/**
 * State for driving find by number/username screen.
 */
data class FindByState(
  val mode: FindByMode,
  val userEntry: String = "",
  val supportedCountryPrefixes: List<CountryPrefix> = PhoneNumberUtil.getInstance().supportedCallingCodes
    .map { CountryPrefix(it, PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(it)) }
    .sortedBy { it.digits.toString() },
  val selectedCountryPrefix: CountryPrefix = supportedCountryPrefixes.first(),
  val countryPrefixSearchEntry: String = "",
  val isLookupInProgress: Boolean = false
) {
  companion object {
    fun startingState(self: Recipient, mode: FindByMode): FindByState {
      val countryCode: Int = try {
        PhoneNumberUtil.getInstance()
          .parse(self.e164.orNull(), null)
          .countryCode
      } catch (e: NumberParseException) {
        -1
      }

      val state = FindByState(mode = mode)
      return state.copy(
        selectedCountryPrefix = state.supportedCountryPrefixes.firstOrNull { it.digits == countryCode } ?: state.supportedCountryPrefixes.first()
      )
    }
  }
}
