/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.countrycode

/**
 * Data class string describing useful characteristics of countries when selecting one. Used in the [CountryCodeState]
 */
data class Country(val emoji: String, val name: String, val countryCode: String)
