/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stories

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import org.thoughtcrime.securesms.main.MainDetailBackStack
import org.thoughtcrime.securesms.main.MainNavigationDetailLocation

/**
 * Controls the navigation stack used by the stories screen.
 */
@OptIn(SavedStateHandleSaveableApi::class)
class StoriesBackStack(savedStateHandle: SavedStateHandle) : MainDetailBackStack {

  companion object {
    private const val KEY = "stories_back_stack"

    val saver: Saver<SnapshotStateList<MainNavigationDetailLocation>, ArrayList<MainNavigationDetailLocation>> = Saver(
      save = { ArrayList(it) },
      restore = { mutableStateListOf(*it.toTypedArray()) }
    )
  }

  override val entries: SnapshotStateList<MainNavigationDetailLocation> = savedStateHandle.saveable(
    key = KEY,
    saver = saver
  ) {
    mutableStateListOf(MainNavigationDetailLocation.Empty)
  }

  override fun push(location: MainNavigationDetailLocation) {
    when {
      location is MainNavigationDetailLocation.Empty || location == entries.lastOrNull() -> Unit

      location.isContentRoot -> {
        entries.removeAll { it !is MainNavigationDetailLocation.Empty }
        entries.add(location)
      }

      else -> entries.add(location)
    }
  }
}
