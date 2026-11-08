/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.invite

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.MainThread
import org.signal.core.util.R

/**
 * Handles 'invite to signal' actions.
 */
object InviteActions {
  /**
   * Opens a share sheet to invite the user to Signal.
   */
  @MainThread
  fun inviteUserToSignal(
    context: Context,
    launchIntent: (Intent) -> Unit,
    inviteText: String = context.getString(
      R.string.Invite__lets_switch_to_signal,
      context.getString(R.string.install_url)
    )
  ) {
    val intent = Intent().apply {
      setAction(Intent.ACTION_SEND)
      setType("text/plain")
      putExtra(Intent.EXTRA_TEXT, inviteText)
    }

    try {
      launchIntent(Intent.createChooser(intent, context.getString(R.string.Invite__invite_to_signal)))
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(context, R.string.Invite__no_app_to_share_to, Toast.LENGTH_LONG).show()
    }
  }
}
