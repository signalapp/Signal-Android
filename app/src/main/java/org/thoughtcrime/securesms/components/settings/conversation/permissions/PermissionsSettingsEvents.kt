package org.thoughtcrime.securesms.components.settings.conversation.permissions

import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason

sealed class PermissionsSettingsEvents {
  class GroupChangeError(val reason: GroupChangeFailureReason) : PermissionsSettingsEvents()
}
