/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

/**
 * Pipeline for subscribing to message backups.
 */
enum class MessageBackupsStage(
  val route: Route
) {
  EDUCATION(route = Route.EDUCATION),
  BACKUP_KEY_EDUCATION(route = Route.BACKUP_KEY_EDUCATION),
  BACKUP_KEY_RECORD(route = Route.BACKUP_KEY_RECORD),
  TYPE_SELECTION(route = Route.TYPE_SELECTION),
  CREATING_IN_APP_PAYMENT(route = Route.TYPE_SELECTION),
  CHECKOUT_SHEET(route = Route.TYPE_SELECTION),
  PROCESS_PAYMENT(route = Route.TYPE_SELECTION),
  PROCESS_FREE(route = Route.TYPE_SELECTION),
  COMPLETED(route = Route.TYPE_SELECTION),
  FAILURE(route = Route.TYPE_SELECTION);

  /**
   * Compose navigation route to display while in a given stage.
   */
  enum class Route {
    EDUCATION,
    BACKUP_KEY_EDUCATION,
    BACKUP_KEY_RECORD,
    TYPE_SELECTION;

    fun isAfter(other: Route): Boolean = ordinal > other.ordinal
  }
}
