/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.vector

import org.thoughtcrime.securesms.avatar.Avatar

sealed interface VectorAvatarCreationAction {
  /** Leave the editor, handing [avatar] back to whoever opened it. */
  data class Finish(val avatar: Avatar.Vector) : VectorAvatarCreationAction

  /** Leave the editor without applying any changes. */
  data object Close : VectorAvatarCreationAction
}
