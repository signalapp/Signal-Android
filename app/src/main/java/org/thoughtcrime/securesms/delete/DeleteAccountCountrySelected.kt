/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.delete

/**
 * Sent through the [org.signal.core.ui.navigation.ResultEventBus] when the user picks a country for the number they
 * confirm when deleting their account.
 */
data class DeleteAccountCountrySelected(val regionCode: String)
