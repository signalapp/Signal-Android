/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats.folders

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.Fragments
import org.signal.core.ui.compose.split.detailEntry

/**
 * Registers the chat folder screens of app settings. Picking a folder's chats is still a View-based fragment, so it is
 * hosted as one.
 */
fun EntryProviderScope<NavKey>.registerChatFoldersRoutes(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.ChatFoldersRoute.ChatFolders> {
    ChatFoldersEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.ChatFoldersRoute.CreateChatFolders> { route ->
    CreateFoldersEntry(folderId = route.folderId, threadIds = route.threadIds, parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.ChatFoldersRoute.ChooseChats> { route ->
    Fragments.Fragment<ChooseChatsFragment>(
      modifier = Modifier.fillMaxSize(),
      arguments = ChooseChatsFragment.createArguments(includeChats = route.includeChats)
    )
  }
}
