/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v3

import org.signal.imageeditor.core.Renderer
import org.signal.mediasend.SentMediaQuality
import org.signal.mediasend.StorySendRequirements
import org.thoughtcrime.securesms.mms.PushMediaConstraints
import org.thoughtcrime.securesms.scribbles.StickerSelectResult
import org.thoughtcrime.securesms.scribbles.toRenderer
import org.thoughtcrime.securesms.stories.Stories

/**
 * Maps the feature-module [StorySendRequirements] to the app-layer [Stories.MediaTransform.SendRequirements].
 */
fun StorySendRequirements.toAppSendRequirements(): Stories.MediaTransform.SendRequirements = when (this) {
  StorySendRequirements.CAN_SEND -> Stories.MediaTransform.SendRequirements.VALID_DURATION
  StorySendRequirements.CAN_NOT_SEND -> Stories.MediaTransform.SendRequirements.CAN_NOT_SEND
  StorySendRequirements.REQUIRES_CROP -> Stories.MediaTransform.SendRequirements.REQUIRES_CLIP
}

/**
 * Maps the app-layer [Stories.MediaTransform.SendRequirements] to the feature-module [StorySendRequirements].
 */
fun Stories.MediaTransform.SendRequirements.toFeatureSendRequirements(): StorySendRequirements = when (this) {
  Stories.MediaTransform.SendRequirements.VALID_DURATION -> StorySendRequirements.CAN_SEND
  Stories.MediaTransform.SendRequirements.CAN_NOT_SEND -> StorySendRequirements.CAN_NOT_SEND
  Stories.MediaTransform.SendRequirements.REQUIRES_CLIP -> StorySendRequirements.REQUIRES_CROP
}

/**
 * Turns a sticker pick into a [Renderer] the image editor can place.
 */
internal fun StickerSelectResult.toRenderer(): Renderer {
  val constraints = PushMediaConstraints(SentMediaQuality.HIGH)
  return toRenderer(constraints.imageMaxWidth, constraints.imageMaxHeight)
}
