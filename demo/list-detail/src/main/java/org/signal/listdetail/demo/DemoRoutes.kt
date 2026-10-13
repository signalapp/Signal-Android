/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.listdetail.demo

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.signal.core.ui.compose.split.DetailNavKey
import org.signal.core.ui.compose.split.ListNavKey

/**
 * The lists this demo can display. Implementing [ListNavKey] is what puts them in the list pane.
 *
 * [INBOX] and [CONTACTS] are tabs, each with a stack of its own. [ARCHIVE] is not: it is pushed onto the
 * inbox's stack, so opening it keeps whatever item was already open beside it.
 */
@Serializable
enum class DemoListRoute(val label: String) : ListNavKey {
  INBOX("Inbox"),
  ARCHIVE("Archive"),
  CONTACTS("Contacts");

  /** The tab this list is displayed under, which is the root of the stack it lives on. */
  val tab: DemoListRoute
    get() = if (this == ARCHIVE) INBOX else this
}

/**
 * The detail content this demo can display above a list. Implementing [DetailNavKey] is what puts them in
 * the detail pane.
 */
@Serializable
sealed interface DemoDetailRoute : DetailNavKey {

  val itemId: Int

  /** An item opened from a list. A content root, so opening another item replaces this one. */
  @Serializable
  data class Item(override val itemId: Int) : DemoDetailRoute {
    override val isContentRoot: Boolean = true
  }

  /** Opened from an [Item]. Not a content root, so it stacks on top of the item instead of replacing it. */
  @Serializable
  data class Notes(override val itemId: Int) : DemoDetailRoute {
    override val isContentRoot: Boolean = false
  }
}

/**
 * Settings, which are neither a list nor detail content: they take the whole window and navigate for
 * themselves once open. A plain [NavKey], since nothing about the panes applies to them, and it stacks
 * above whatever was open so that popping it puts the panes back the way they were.
 */
@Serializable
data object DemoSettingsRoute : NavKey
