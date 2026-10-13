/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.listdetail.demo

/** A row in a list, and the thing the detail pane displays. */
data class DemoItem(val id: Int, val title: String, val subtitle: String)

/**
 * The contents of each list. All static: the demo is about navigation, not about data.
 */
object DemoData {

  val inbox: List<DemoItem> = listOf(
    DemoItem(1, "Nadia", "Sent a photo"),
    DemoItem(2, "Weekend Plans", "Miguel: I can drive"),
    DemoItem(3, "Priya", "Thanks!"),
    DemoItem(4, "Book Club", "Ana: Chapter four tonight"),
    DemoItem(5, "Sam", "Are you around later?"),
    DemoItem(6, "Deniz", "Sent a voice message"),
    DemoItem(7, "Roommates", "Jo: Trash goes out tomorrow"),
    DemoItem(8, "Ines", "See you then")
  )

  val archive: List<DemoItem> = listOf(
    DemoItem(101, "Old Group", "Archived last spring"),
    DemoItem(102, "Yusuf", "Archived in March"),
    DemoItem(103, "Delivery Updates", "Archived in January")
  )

  val contacts: List<DemoItem> = listOf(
    DemoItem(201, "Ana", "+1 555 0100"),
    DemoItem(202, "Deniz", "+1 555 0101"),
    DemoItem(203, "Ines", "+1 555 0102"),
    DemoItem(204, "Miguel", "+1 555 0103"),
    DemoItem(205, "Nadia", "+1 555 0104"),
    DemoItem(206, "Priya", "+1 555 0105"),
    DemoItem(207, "Sam", "+1 555 0106"),
    DemoItem(208, "Yusuf", "+1 555 0107")
  )

  private val byId: Map<Int, DemoItem> = (inbox + archive + contacts).associateBy { it.id }

  fun itemsFor(route: DemoListRoute): List<DemoItem> = when (route) {
    DemoListRoute.INBOX -> inbox
    DemoListRoute.ARCHIVE -> archive
    DemoListRoute.CONTACTS -> contacts
  }

  operator fun get(id: Int): DemoItem = byId.getValue(id)
}
