/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.locale

enum class LanguageAvailability {
  /** Locale is already on disk and can be applied immediately. */
  Installed,

  /** Locale is missing but Play Feature Delivery can fetch it. */
  NeedsDownload,

  /** Locale is missing and the Play Store that would fetch it is disabled. */
  PlayStoreDisabled,

  /** Locale is missing and the Play Store cannot fetch it because Google Play services is disabled. */
  PlayServicesDisabled,

  /** Locale is missing and there is no Play Store to fetch it. */
  PlayStoreMissing
}
