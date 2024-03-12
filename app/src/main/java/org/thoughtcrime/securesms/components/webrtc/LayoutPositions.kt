/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.components.webrtc

import androidx.annotation.Dimension
import androidx.annotation.IdRes
import androidx.constraintlayout.widget.ConstraintSet
import org.thoughtcrime.securesms.R

/** Constraints to apply for different call sizes */
enum class LayoutPositions(
  @JvmField @IdRes val participantBottomViewId: Int,
  @JvmField @Dimension val participantBottomMargin: Int,
  @JvmField @IdRes val reactionBottomViewId: Int,
  @JvmField @Dimension val reactionBottomMargin: Int
) {
  /** 1:1 or small calls anchor full screen or controls */
  SMALL_GROUP(
    participantBottomViewId = ConstraintSet.PARENT_ID,
    participantBottomMargin = 0,
    reactionBottomViewId = R.id.call_screen_pending_recipients,
    reactionBottomMargin = 8
  ),

  /** Large calls have a participant rail to anchor to */
  LARGE_GROUP(
    participantBottomViewId = R.id.call_screen_participants_recycler,
    participantBottomMargin = 16,
    reactionBottomViewId = R.id.call_screen_pending_recipients,
    reactionBottomMargin = 20
  );

  @JvmField
  val participantBottomViewEndSide: Int = if (participantBottomViewId == ConstraintSet.PARENT_ID) ConstraintSet.BOTTOM else ConstraintSet.TOP
}
