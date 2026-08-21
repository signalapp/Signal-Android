/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.shared

import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * A recipient that can render in a preview. It needs an id, or it'd be equal to [Recipient.UNKNOWN] and the screen
 * would render as empty, and it needs to already be resolved, since resolving one reads the database.
 */
fun previewRecipient(
  id: Long,
  profileName: ProfileName = ProfileName.EMPTY,
  about: String? = null,
  groupName: String? = null,
  groupId: GroupId? = null,
  isSelf: Boolean = false,
  isReleaseNotes: Boolean = false,
  isBlocked: Boolean = false
): Recipient {
  return Recipient(
    id = RecipientId.from(id),
    isResolving = false,
    profileName = profileName,
    about = about,
    groupName = groupName,
    groupIdValue = groupId,
    isActiveGroup = groupId != null,
    isSelf = isSelf,
    isReleaseNotes = isReleaseNotes,
    isBlocked = isBlocked
  )
}

/**
 * Built by parsing an encoded id rather than deriving one from a GroupMasterKey, since deriving needs libsignal's
 * native library and the preview renderer isn't allowed to load it.
 */
val PREVIEW_GROUP_ID: GroupId by lazy { GroupId.parseOrThrow("__signal_group__v2__!" + "01".repeat(32)) }
