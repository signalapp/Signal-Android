/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.countrycode

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Data class string describing useful characteristics of countries when selecting one. Used in the [CountryCodeState]
 * An example is: Country(emoji=🇺🇸, name = "United States", countryCode = 1, regionCode= "US")
 */
@Parcelize
data class Country(val emoji: String, val name: String, val countryCode: Int, val regionCode: String) : Parcelable
