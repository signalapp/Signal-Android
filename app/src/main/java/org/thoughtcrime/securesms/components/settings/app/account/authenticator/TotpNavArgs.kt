/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import android.os.Bundle
import org.signal.appsettings.totp.TotpApp

/**
 * The nav arguments the authenticator app screens pass between each other, and the parsing that turns them back into
 * something typed. Keep the values in sync with the argument defaults declared for these destinations in
 * app_settings_with_change_number.xml.
 */
object TotpNavArgs {

  /** The app being acted on. [NO_APP_ID] when nothing identified one. */
  const val ARG_APP_ID = "app_id"
  const val NO_APP_ID = -1L

  /** Whether the screen is renaming an existing app rather than naming a newly paired one. */
  const val ARG_RENAMING = "renaming"

  /** The rest of the app being renamed. Absent when the screen is naming a newly paired app instead. */
  const val ARG_APP_NAME = "app_name"
  const val ARG_APP_CREATED_AT = "app_created_at"
  const val NO_CREATED_AT = -1L

  /** The app id in [arguments], or null when there isn't one. */
  fun appId(arguments: Bundle?): Long? = arguments?.getLong(ARG_APP_ID, NO_APP_ID)?.takeIf { it != NO_APP_ID }

  /** Packs [app] into [bundle] for the rename flow, so the name screen doesn't have to fetch what the list already had. */
  fun putRenamedApp(bundle: Bundle, app: TotpApp) {
    bundle.putLong(ARG_APP_ID, app.id)
    bundle.putBoolean(ARG_RENAMING, true)
    bundle.putString(ARG_APP_NAME, app.name)
    bundle.putLong(ARG_APP_CREATED_AT, app.createdAt ?: NO_CREATED_AT)
  }

  /** The app being renamed, or null when [arguments] describe naming a newly paired app rather than a rename. */
  fun renamedApp(arguments: Bundle?): TotpApp? {
    if (arguments == null || !arguments.getBoolean(ARG_RENAMING, false)) {
      return null
    }

    val appId = appId(arguments) ?: return null

    return TotpApp(
      id = appId,
      name = arguments.getString(ARG_APP_NAME)?.takeIf { it.isNotEmpty() },
      createdAt = arguments.getLong(ARG_APP_CREATED_AT, NO_CREATED_AT).takeIf { it != NO_CREATED_AT }
    )
  }
}
