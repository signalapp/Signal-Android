/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.editprofile

/**
 * Reminder that these events are logged, so don't include anything sensitive in the toString.
 */
sealed interface AvatarPreviewEvent {

  /** The user tapped the navigation (back) icon. */
  data object NavigateBackClicked : AvatarPreviewEvent

  /** The user dragged the avatar far enough to dismiss it. */
  data object AvatarDraggedAway : AvatarPreviewEvent

  /** The user tapped the avatar, which shows or hides the toolbar. */
  data object AvatarClicked : AvatarPreviewEvent
}
