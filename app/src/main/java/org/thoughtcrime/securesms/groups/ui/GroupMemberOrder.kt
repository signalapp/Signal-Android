/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui

import java.text.Collator

/**
 * Comparator factory for sorting group members consistently across the app.
 *
 * The canonical sort order is: self first, then admins, then members with a user-set display name, then alphabetical by display name.
 */
object GroupMemberOrder {
  private val collator: Collator = Collator.getInstance()
    .apply { strength = Collator.PRIMARY }

  /**
   * Creates a [Comparator] for any type [T] using the canonical group member sort order.
   *
   * The caller supplies all sort-keys, so this utility can be applied to any data type that models a group member.
   */
  @JvmStatic
  fun <T> comparator(
    isSelf: (T) -> Boolean,
    isAdmin: (T) -> Boolean,
    hasDisplayName: (T) -> Boolean,
    getDisplayName: (T) -> String
  ): Comparator<T> = compareBy<T> { !isSelf(it) }
    .thenBy { !isAdmin(it) }
    .then(displayNameComparator(hasDisplayName, getDisplayName))

  /**
   * Creates a [Comparator] for just the display-name portion of the canonical order: members with a user-set display name
   * first, then alphabetically by display name using locale-aware collation.
   *
   * Useful for member lists that have no notion of self-first or admin-first ordering.
   */
  @JvmStatic
  fun <T> displayNameComparator(
    hasDisplayName: (T) -> Boolean,
    getDisplayName: (T) -> String
  ): Comparator<T> = compareBy<T> { !hasDisplayName(it) }
    .thenBy { collator.getCollationKey(getDisplayName(it)) }
}
