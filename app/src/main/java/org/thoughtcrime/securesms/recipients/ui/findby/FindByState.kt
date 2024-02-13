/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.findby

import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.thoughtcrime.securesms.registration.util.CountryPrefix

data class FindByState(
  val mode: FindByMode,
  val userEntry: String = "",
  val supportedCountryPrefixes: List<CountryPrefix> = PhoneNumberUtil.getInstance().supportedCallingCodes
    .map { CountryPrefix(it, PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(it)) }
    .sortedBy { it.digits.toString() },
  val selectedCountryPrefix: CountryPrefix = supportedCountryPrefixes.first(),
  val countryPrefixSearchEntry: String = "",
  val isLookupInProgress: Boolean = false
)
