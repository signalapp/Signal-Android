/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v3

import org.signal.mediasend.StorySendRequirements
import org.thoughtcrime.securesms.stories.Stories

/**
 * Maps the feature-module [StorySendRequirements] to the app-layer [Stories.MediaTransform.SendRequirements].
 */
fun StorySendRequirements.toAppSendRequirements(): Stories.MediaTransform.SendRequirements = when (this) {
  StorySendRequirements.CAN_SEND -> Stories.MediaTransform.SendRequirements.VALID_DURATION
  StorySendRequirements.CAN_NOT_SEND -> Stories.MediaTransform.SendRequirements.CAN_NOT_SEND
  StorySendRequirements.REQUIRES_CROP -> Stories.MediaTransform.SendRequirements.REQUIRES_CLIP
}
