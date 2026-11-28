/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.twofactor

import android.os.Bundle
import org.signal.appsettings.account.TwoFactorMethod

/**
 * The nav arguments the two-factor screens pass between each other, and the parsing that turns them back into
 * something typed. Keep the values in sync with the argument defaults declared for these destinations in
 * app_settings_with_change_number.xml.
 */
object TwoFactorNavArgs {

  /** The method being acted on. [NO_METHOD_ID] when nothing identified one. */
  const val ARG_METHOD_ID = "method_id"
  const val NO_METHOD_ID = -1L

  /** What sort of second factor is being named, which decides the screen's copy. */
  const val ARG_METHOD_KIND = "method_kind"

  /**
   * Whether the screen is renaming a method that already exists rather than naming one that was just added. A method
   * whose metadata we couldn't read has no name and no date, so neither of those can stand in for this.
   */
  const val ARG_RENAMING = "renaming"

  /** The rest of the method being renamed. Absent when the screen is naming a newly added method instead. */
  const val ARG_METHOD_NAME = "method_name"
  const val ARG_METHOD_CREATED_AT = "method_created_at"
  const val NO_CREATED_AT = -1L

  /** The method id in [arguments], or null when there isn't one. */
  fun methodId(arguments: Bundle?): Long? = arguments?.getLong(ARG_METHOD_ID, NO_METHOD_ID)?.takeIf { it != NO_METHOD_ID }

  /** The kind in [arguments], falling back to an authenticator app when nothing said otherwise. */
  fun methodKind(arguments: Bundle?): TwoFactorMethod.Kind {
    val name = arguments?.getString(ARG_METHOD_KIND) ?: return TwoFactorMethod.Kind.AUTHENTICATOR_APP
    return TwoFactorMethod.Kind.entries.firstOrNull { it.name == name } ?: TwoFactorMethod.Kind.AUTHENTICATOR_APP
  }

  /** When the method being named was added, which has to survive the trip so renaming doesn't rewrite it. */
  fun createdAt(arguments: Bundle?): Long? = arguments?.getLong(ARG_METHOD_CREATED_AT, NO_CREATED_AT)?.takeIf { it != NO_CREATED_AT }

  /** Packs [method] into [bundle] for the rename flow, so the name screen doesn't have to fetch what the list already had. */
  fun putRenamedMethod(bundle: Bundle, method: TwoFactorMethod) {
    bundle.putLong(ARG_METHOD_ID, method.id)
    bundle.putBoolean(ARG_RENAMING, true)
    bundle.putString(ARG_METHOD_KIND, method.kind.name)
    bundle.putString(ARG_METHOD_NAME, method.name)
    bundle.putLong(ARG_METHOD_CREATED_AT, method.createdAt ?: NO_CREATED_AT)
  }

  /** Packs a method that was just added and still has its default name, which the name screen is about to replace. */
  fun putNewMethod(bundle: Bundle, method: TwoFactorMethod) {
    bundle.putLong(ARG_METHOD_ID, method.id)
    bundle.putString(ARG_METHOD_KIND, method.kind.name)
    bundle.putLong(ARG_METHOD_CREATED_AT, method.createdAt ?: NO_CREATED_AT)
  }

  /** The method being renamed, or null when [arguments] describe naming a newly added method rather than a rename. */
  fun renamedMethod(arguments: Bundle?): TwoFactorMethod? {
    if (arguments == null || !arguments.getBoolean(ARG_RENAMING, false)) {
      return null
    }

    val id = methodId(arguments) ?: return null

    return TwoFactorMethod(
      id = id,
      kind = methodKind(arguments),
      name = arguments.getString(ARG_METHOD_NAME)?.takeIf { it.isNotEmpty() },
      createdAt = createdAt(arguments)
    )
  }
}
