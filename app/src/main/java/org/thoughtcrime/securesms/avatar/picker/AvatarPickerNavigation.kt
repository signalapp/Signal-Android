/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.picker

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.split.detailEntry
import org.signal.core.ui.navigation.LocalResultEventBus

/**
 * Registers the avatar picker for the user's own avatar. What they pick is sent to the host's result bus as an
 * [AvatarPickerResult].
 */
fun EntryProviderScope<NavKey>.registerAvatarPickerScreens() {
  detailEntry<AppSettingsRoute.AccountRoute.AvatarPicker> {
    val resultBus = LocalResultEventBus.current

    AvatarPickerEntry(onResult = { result -> resultBus.sendResult<AvatarPickerResult>(result = result) })
  }
}
