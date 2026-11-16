/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.twofactor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.account.TwoFactorMethod
import org.signal.appsettings.account.TwoFactorMethodNamed
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.PopUpTo
import org.signal.appsettings.routes.navigateBack
import org.signal.appsettings.twofactornameentry.TwoFactorNameEntryAction
import org.signal.appsettings.twofactornameentry.TwoFactorNameEntryScreen
import org.signal.core.ui.compose.CollectActions

/**
 * Names a second factor, either one that was just added ([methodId], [kind] and [createdAt]) or [renamedMethod]. Once
 * it has its name, [onMethodNamed] gets the result for account settings and the screen returns there.
 */
@Composable
fun TwoFactorNameEntryEntry(
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit,
  methodId: Long?,
  kind: TwoFactorMethod.Kind,
  createdAt: Long?,
  renamedMethod: TwoFactorMethod?,
  onMethodNamed: (TwoFactorMethodNamed) -> Unit
) {
  val viewModel: TwoFactorNameEntryViewModel = viewModel {
    TwoFactorNameEntryViewModel(
      parentEventEmitter = parentEventEmitter,
      methodId = methodId,
      kind = kind,
      createdAt = createdAt ?: System.currentTimeMillis(),
      renamedMethod = renamedMethod
    )
  }
  val state by viewModel.state.collectAsStateWithLifecycle()

  CollectActions(viewModel.actions) { action ->
    when (action) {
      is TwoFactorNameEntryAction.MethodNamed -> {
        onMethodNamed(action.result)
        parentEventEmitter.navigateBack(popUpTo = PopUpTo(AppSettingsRoute.AccountRoute.Account::class))
      }
    }
  }

  TwoFactorNameEntryScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}

/** The method a [AppSettingsRoute.AccountRoute.RenameTwoFactorMethod] identifies, rebuilt from the fields it carries. */
fun AppSettingsRoute.AccountRoute.RenameTwoFactorMethod.toTwoFactorMethod(): TwoFactorMethod {
  return TwoFactorMethod(id = methodId, kind = methodKind, name = methodName, createdAt = methodCreatedAt)
}

/** The method a [AppSettingsRoute.AccountRoute.NameNewTwoFactorMethod] identifies, which has no name yet. */
fun AppSettingsRoute.AccountRoute.NameNewTwoFactorMethod.toTwoFactorMethod(): TwoFactorMethod {
  return TwoFactorMethod(id = methodId, kind = methodKind, name = null, createdAt = methodCreatedAt)
}
