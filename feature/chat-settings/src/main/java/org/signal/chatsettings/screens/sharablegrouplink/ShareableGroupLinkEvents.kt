/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.chatsettings.screens.sharablegrouplink

/**
 * Represents everything that can happen on the shareable group link screen: what the user does with the link, plus
 * the link itself changing underneath us.
 */
sealed interface ShareableGroupLinkEvents {

  /**
   * The group's link changed, either because we just changed it or because another admin did.
   */
  data class GroupLinkChanged(val groupLink: GroupLink) : ShareableGroupLinkEvents

  /**
   * User turned the group link on or off.
   */
  data object GroupLinkToggled : ShareableGroupLinkEvents

  /**
   * User turned the requirement that an admin approve new members on or off.
   */
  data object AdminApprovalToggled : ShareableGroupLinkEvents

  /**
   * User asked to reset the link. Anyone holding the current link loses access, so this asks for confirmation first.
   */
  data object ResetLinkClicked : ShareableGroupLinkEvents

  /**
   * User accepted that resetting the link stops the current one from working.
   */
  data object ResetLinkConfirmed : ShareableGroupLinkEvents

  /**
   * User dismissed the dialog that was showing.
   */
  data object DialogDismissed : ShareableGroupLinkEvents

  /**
   * The snackbar reporting a rejected change has come and gone.
   */
  data object SnackbarDismissed : ShareableGroupLinkEvents
}
