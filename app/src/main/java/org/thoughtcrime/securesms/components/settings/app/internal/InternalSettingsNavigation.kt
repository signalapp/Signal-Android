/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.internal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.Fragments
import org.signal.core.ui.compose.split.detailEntry
import org.thoughtcrime.securesms.components.settings.app.internal.backup.InternalBackupPlaygroundFragment
import org.thoughtcrime.securesms.components.settings.app.internal.conversation.springboard.InternalConversationSpringboardFragment
import org.thoughtcrime.securesms.components.settings.app.internal.conversation.test.InternalConversationTestFragment
import org.thoughtcrime.securesms.components.settings.app.internal.dataseeding.DataSeedingPlaygroundFragment
import org.thoughtcrime.securesms.components.settings.app.internal.donor.InternalDonorErrorConfigurationEntry
import org.thoughtcrime.securesms.components.settings.app.internal.issues.InternalIssuesEntry
import org.thoughtcrime.securesms.components.settings.app.internal.remoteconfig.InternalRemoteConfigEntry
import org.thoughtcrime.securesms.components.settings.app.internal.search.InternalSearchEntry
import org.thoughtcrime.securesms.components.settings.app.internal.sqlite.InternalSqlitePlaygroundEntry
import org.thoughtcrime.securesms.components.settings.app.internal.storage.InternalStorageServicePlaygroundEntry
import org.thoughtcrime.securesms.components.settings.app.internal.svr.InternalSvrPlaygroundEntry

/**
 * Registers the routes within the internal section of app settings. Screens that are already Compose get their entry
 * content directly; the rest are hosted as their fragments.
 */
fun EntryProviderScope<NavKey>.registerInternalRoutes(parentEventEmitter: (AppSettingsNavigationEvent) -> Unit) {
  detailEntry<AppSettingsRoute.InternalRoute.Internal> {
    Fragments.Fragment<InternalSettingsFragment>(modifier = Modifier.fillMaxSize())
  }

  detailEntry<AppSettingsRoute.InternalRoute.RemoteConfig> {
    InternalRemoteConfigEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.InternalRoute.Issues> {
    InternalIssuesEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.InternalRoute.SqlitePlayground> {
    InternalSqlitePlaygroundEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.InternalRoute.BackupPlayground> {
    Fragments.Fragment<InternalBackupPlaygroundFragment>(modifier = Modifier.fillMaxSize())
  }

  detailEntry<AppSettingsRoute.InternalRoute.StorageServicePlayground> {
    InternalStorageServicePlaygroundEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.InternalRoute.SvrPlayground> {
    InternalSvrPlaygroundEntry()
  }

  detailEntry<AppSettingsRoute.InternalRoute.DataSeedingPlayground> {
    Fragments.Fragment<DataSeedingPlaygroundFragment>(modifier = Modifier.fillMaxSize())
  }

  detailEntry<AppSettingsRoute.InternalRoute.Search> {
    InternalSearchEntry()
  }

  detailEntry<AppSettingsRoute.InternalRoute.DonorErrorConfiguration> {
    InternalDonorErrorConfigurationEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.InternalRoute.OneTimeDonationConfiguration> {
    InternalPendingOneTimeDonationConfigurationEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.InternalRoute.TerminalDonationConfiguration> {
    InternalTerminalDonationConfigurationEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.InternalRoute.StoryDialogs> {
    InternalStoryDialogLauncherEntry(parentEventEmitter = parentEventEmitter)
  }

  detailEntry<AppSettingsRoute.InternalRoute.ChatSpringboard> {
    Fragments.Fragment<InternalConversationSpringboardFragment>(modifier = Modifier.fillMaxSize())
  }

  detailEntry<AppSettingsRoute.InternalRoute.ConversationTestFragment> {
    Fragments.Fragment<InternalConversationTestFragment>(modifier = Modifier.fillMaxSize())
  }
}
