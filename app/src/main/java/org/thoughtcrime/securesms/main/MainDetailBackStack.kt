/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.runtime.snapshots.SnapshotStateList

interface MainDetailBackStack {
  val entries: SnapshotStateList<MainNavigationDetailLocation>

  val isEmpty: Boolean
    get() = entries.singleOrNull() is MainNavigationDetailLocation.Empty

  fun push(location: MainNavigationDetailLocation)

  /**
   * Pops the top entry off the stack. Returns true if something was popped, false if the stack is already at its root.
   */
  fun pop(): Boolean {
    if (entries.size <= 1) return false
    entries.removeAt(entries.lastIndex)
    return true
  }

  /**
   * Resets the stack to its base empty state.
   */
  fun reset() {
    entries.removeAll { it !is MainNavigationDetailLocation.Empty }
    if (entries.isEmpty()) {
      entries.add(MainNavigationDetailLocation.Empty)
    }
  }
}
