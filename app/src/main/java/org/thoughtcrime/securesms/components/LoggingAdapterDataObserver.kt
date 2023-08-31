/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components

import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import org.signal.core.util.logging.Log

class LoggingAdapterDataObserver(
  private val tag: String
) : AdapterDataObserver() {
  override fun onChanged() {
    Log.d(tag, "onChanged() called")
  }

  override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
    Log.d(tag, "onItemRangeChanged() called with: positionStart = $positionStart, itemCount = $itemCount")
  }

  override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
    Log.d(tag, "onItemRangeChanged() called with: positionStart = $positionStart, itemCount = $itemCount, payload = $payload")
  }

  override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
    Log.d(tag, "onItemRangeInserted() called with: positionStart = $positionStart, itemCount = $itemCount")
  }

  override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
    Log.d(tag, "onItemRangeRemoved() called with: positionStart = $positionStart, itemCount = $itemCount")
  }

  override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
    Log.d(tag, "onItemRangeMoved() called with: fromPosition = $fromPosition, toPosition = $toPosition, itemCount = $itemCount")
  }

  override fun onStateRestorationPolicyChanged() {
    Log.d(tag, "onStateRestorationPolicyChanged() called")
  }
}
