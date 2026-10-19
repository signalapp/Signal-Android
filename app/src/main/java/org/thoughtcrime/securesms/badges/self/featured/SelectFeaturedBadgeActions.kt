/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self.featured

/**
 * One-off actions carried out by the fragment.
 */
sealed interface SelectFeaturedBadgeActions {
  /** Saving the featured badge to the profile failed. */
  data object ShowFailedToUpdateProfile : SelectFeaturedBadgeActions
}
