/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.badges.view

import org.signal.core.models.badges.Badge

/**
 * What [ViewBadgeSheetContent] shows: someone's badges, and whether to invite the viewer to earn one.
 */
data class ViewBadgeSheetState(
  val badges: List<Badge> = emptyList(),
  /** The badge to open on, or the first if it isn't among [badges]. */
  val startBadge: Badge? = null,
  val shortName: String = "",
  /** False when the badges are the viewer's own, since they've already earned them. */
  val showAction: Boolean = true,
  /** Whether this device can donate in the app, or has to be sent to the website instead. */
  val canDonateInApp: Boolean = true
)
