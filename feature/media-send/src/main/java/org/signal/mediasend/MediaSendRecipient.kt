/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A recipient the flow will send to, along with whether the send targets that recipient's story.
 *
 * Story-ness is per-recipient rather than per-flow because a single send can mix targets, e.g. a group
 * chat alongside that same group's story.
 */
@Parcelize
data class MediaSendRecipient(
  val id: MediaRecipientId,
  val isStory: Boolean = false
) : Parcelable
