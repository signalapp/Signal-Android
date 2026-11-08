/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.split

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Keys the split-pane tests navigate with. Serializable because the back stack is persisted through
 * `kotlinx.serialization`, which resolves each entry's serializer from its concrete class.
 */

/** The list a stack is rooted at, a list pushed on top of it, and the root of a different stack. */
@Serializable
enum class TestListKey : ListNavKey {
  ROOT,
  PUSHED,
  OTHER
}

/** Detail content root, so pushing one replaces the detail content already displayed. */
@Serializable
data class TestDetailKey(val id: Int) : DetailNavKey {
  override val isContentRoot: Boolean get() = true
}

/** Not a content root, so pushing one stacks on top of the detail content already displayed. */
@Serializable
data class TestSubScreenKey(val id: Int, val label: String = "") : DetailNavKey {
  override val isContentRoot: Boolean get() = false
}

/** Neither a list nor detail content: a screen that takes the whole window. */
@Serializable
data object TestFullScreenKey : NavKey
