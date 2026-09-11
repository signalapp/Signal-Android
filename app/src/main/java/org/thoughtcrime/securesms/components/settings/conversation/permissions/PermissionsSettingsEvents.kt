/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.permissions

import org.thoughtcrime.securesms.components.settings.conversation.permissions.PermissionsSettingsRepository.GroupPermissions

/**
 * Represents everything that can happen on the group permissions screen: the permissions the user picks, plus the
 * group's own permissions changing underneath us.
 *
 * Each permission is expressed as whether non-admins are allowed to do the thing, matching how the rows read.
 */
sealed interface PermissionsSettingsEvents {

  /**
   * The group's permissions changed, either because we just changed them or because someone else did.
   */
  data class PermissionsChanged(val permissions: GroupPermissions) : PermissionsSettingsEvents

  /**
   * User picked who can add new members.
   */
  data class SetNonAdminCanAddMembers(val allowed: Boolean) : PermissionsSettingsEvents

  /**
   * User picked who can edit the group's name, avatar, and description.
   */
  data class SetNonAdminCanEditGroupInfo(val allowed: Boolean) : PermissionsSettingsEvents

  /**
   * User picked who can send messages and start calls. Restricting it to admins makes this an announcement group.
   */
  data class SetNonAdminCanSendMessages(val allowed: Boolean) : PermissionsSettingsEvents

  /**
   * User picked who can add member labels. Restricting it to admins clears the labels non-admins have already set, so
   * that direction asks for confirmation first.
   */
  data class SetNonAdminCanSetMemberLabel(val allowed: Boolean) : PermissionsSettingsEvents

  /**
   * User accepted that restricting member labels to admins will clear the ones non-admins set.
   */
  data object MemberLabelsWillBeClearedConfirmed : PermissionsSettingsEvents

  /**
   * User dismissed the dialog that was showing.
   */
  data object DialogDismissed : PermissionsSettingsEvents

  /**
   * The snackbar reporting a rejected change has come and gone.
   */
  data object SnackbarDismissed : PermissionsSettingsEvents
}
