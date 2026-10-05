/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.invites

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.MainThread
import org.thoughtcrime.securesms.util.CommunicationActions
import org.signal.appsettings.R as AppSettingsR
import org.signal.core.util.R as CoreUtilsR

/**
 * Handles 'invite to signal' actions.
 */
object InviteActions {
  /**
   * Called to send a message to a user to invite them to Signal.
   * The invite can be sent in one of three ways:
   *
   * 1. If Signal is the user's default SMS app, we can simply append the message to the composer.
   * 2. If the user has an sms address, we generate a SENDTO intent and request it to be launched.
   * 3. Otherwise, we generate a share intent to allow the user to select how to send the invite.
   */
  @MainThread
  fun inviteUserToSignal(
    context: Context,
    launchIntent: (Intent) -> Unit
  ) {
    val inviteText = context.getString(
      CoreUtilsR.string.Invite__lets_switch_to_signal,
      context.getString(CoreUtilsR.string.install_url)
    )
    val intent = CommunicationActions.createIntentToShareTextViaShareSheet(inviteText)

    try {
      launchIntent(Intent.createChooser(intent, context.getString(AppSettingsR.string.InviteScreen__invite_to_signal)))
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(context, AppSettingsR.string.InviteScreen__no_app_to_share_to, Toast.LENGTH_LONG).show()
    }
  }
}
