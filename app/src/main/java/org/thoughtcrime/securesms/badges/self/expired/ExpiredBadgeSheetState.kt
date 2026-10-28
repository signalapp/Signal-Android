/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.self.expired

import org.signal.core.models.badges.Badge

/**
 * What [ExpiredBadgeSheetContent] shows: a badge that has run out, and how to win it back.
 */
data class ExpiredBadgeSheetState(
  val badge: Badge,
  /** Whether the user probably still has a monthly donation, so is invited to boost rather than to subscribe. */
  val isLikelyASustainer: Boolean = false
)
