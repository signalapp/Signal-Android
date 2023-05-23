/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.view.MotionEvent
import androidx.lifecycle.ViewModel

/**
 * Allows an activity to notify the fragment of a stream of motion events.
 */
class MotionEventRelay : ViewModel() {

  private var drain: Drain? = null

  fun setDrain(drain: Drain?) {
    this.drain = drain
  }

  fun offer(motionEvent: MotionEvent?): Boolean {
    return motionEvent?.let { drain?.accept(it) } ?: false
  }

  interface Drain {
    fun accept(motionEvent: MotionEvent): Boolean
  }
}
