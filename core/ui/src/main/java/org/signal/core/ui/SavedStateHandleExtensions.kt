/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.serialization.saved
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import kotlin.properties.ReadWriteProperty

/**
 * Creates a persistable backstack that can be owned by a ViewModel.
 */
fun SavedStateHandle.backStack(
  key: String,
  root: NavKey
): ReadWriteProperty<Any?, NavBackStack<NavKey>> {
  return saved(
    serializer = NavBackStackSerializer(NavKeySerializer()),
    key = key
  ) {
    NavBackStack(root)
  }
}

/**
 * The same persistable backstack as [backStack], for an owner that holds several of them and so cannot
 * name each one as a property.
 */
fun SavedStateHandle.createBackStack(key: String, root: NavKey): NavBackStack<NavKey> {
  return BackStackHolder(this, key, root).backStack
}

private class BackStackHolder(savedStateHandle: SavedStateHandle, key: String, root: NavKey) {
  val backStack: NavBackStack<NavKey> by savedStateHandle.backStack(key, root)
}
