/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.polls

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Class to track someone who has voted in an option within a poll.
 */
@Parcelize
data class Voter(
  val id: Long,
  val voteCount: Int
) : Parcelable
