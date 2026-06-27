/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms

/**
 * Used in our [BuildConfig] to tie together the various attributes of a KBS instance. This
 * is sitting in the root directory so it can be accessed by the build config.
 */
data class KbsEnclave(
    val enclaveName: String,
    val serviceId: String,
    val mrEnclave: String
)
