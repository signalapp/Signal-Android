/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.notificationprofilecreated

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface NotificationProfileCreatedEvent {

  /** The user tapped the done button. */
  data object DoneClicked : NotificationProfileCreatedEvent
}
