/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage

enum class UsernameEditMode {
  /** A typical launch, no special conditions. */
  NORMAL,

  /** Screen is launched during registration, includes special first-time flows. */
  REGISTRATION,

  /** Screen was launched because the username was in a bad state and needs to be recovered. Shows a special dialog. */
  RECOVERY;

  val allowsDelete get() = this == NORMAL || this == RECOVERY
}
