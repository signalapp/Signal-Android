/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items

import android.animation.LayoutTransition
import org.thoughtcrime.securesms.conversation.mutiselect.ConversationItemAnimator

/**
 * Fades out footer views as they are hidden, such as when a newer message is sent and the previous bubble drops its
 * footer. Bubble bounds are animated separately by [V2BubbleBoundsAnimator].
 */
class V2FooterFadeTransition : LayoutTransition() {
  init {
    disableTransitionType(APPEARING)
    disableTransitionType(CHANGE_APPEARING)
    disableTransitionType(CHANGE_DISAPPEARING)
    disableTransitionType(CHANGING)
    setAnimateParentHierarchy(false)

    setDuration(DISAPPEARING, 150)
    setStartDelay(DISAPPEARING, 0)
    setInterpolator(DISAPPEARING, ConversationItemAnimator.SLIDE_INTERPOLATOR)
  }
}
