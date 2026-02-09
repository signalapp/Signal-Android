/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.memberlabel

import androidx.annotation.ColorInt

/**
 * A member's custom label within a group.
 */
data class MemberLabel(
  val emoji: String?,
  val text: String
)

data class StyledMemberLabel(
  val label: MemberLabel,
  @param:ColorInt val tintColor: Int
)
