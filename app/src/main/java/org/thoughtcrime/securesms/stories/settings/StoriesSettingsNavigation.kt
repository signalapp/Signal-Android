/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stories.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.Fragments
import org.signal.core.ui.compose.split.detailEntry
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.stories.settings.custom.PrivateStorySettingsFragment
import org.thoughtcrime.securesms.stories.settings.custom.PrivateStorySettingsFragmentArgs
import org.thoughtcrime.securesms.stories.settings.custom.name.EditStoryNameFragment
import org.thoughtcrime.securesms.stories.settings.custom.name.EditStoryNameFragmentArgs
import org.thoughtcrime.securesms.stories.settings.custom.viewers.AddViewersFragment
import org.thoughtcrime.securesms.stories.settings.custom.viewers.AddViewersFragmentArgs
import org.thoughtcrime.securesms.stories.settings.group.GroupStorySettingsFragment
import org.thoughtcrime.securesms.stories.settings.group.GroupStorySettingsFragmentArgs
import org.thoughtcrime.securesms.stories.settings.my.MyStorySettingsFragment
import org.thoughtcrime.securesms.stories.settings.privacy.AllExceptFragment
import org.thoughtcrime.securesms.stories.settings.privacy.OnlyShareWithFragment
import org.thoughtcrime.securesms.stories.settings.story.StoriesPrivacySettingsFragment
import org.thoughtcrime.securesms.stories.settings.story.StoriesPrivacySettingsFragmentArgs

/**
 * Registers the stories section of app settings. Its screens are still fragments, so each is hosted as one.
 */
fun EntryProviderScope<NavKey>.registerStoriesRoutes() {
  detailEntry<AppSettingsRoute.StoriesRoute.Privacy> { route ->
    Fragments.Fragment<StoriesPrivacySettingsFragment>(
      modifier = Modifier.fillMaxSize(),
      arguments = StoriesPrivacySettingsFragmentArgs.Builder(route.titleId).build().toBundle()
    )
  }

  detailEntry<AppSettingsRoute.StoriesRoute.MyStory> {
    Fragments.Fragment<MyStorySettingsFragment>(modifier = Modifier.fillMaxSize())
  }

  detailEntry<AppSettingsRoute.StoriesRoute.AllExcept> {
    Fragments.Fragment<AllExceptFragment>(modifier = Modifier.fillMaxSize())
  }

  detailEntry<AppSettingsRoute.StoriesRoute.OnlyShareWith> {
    Fragments.Fragment<OnlyShareWithFragment>(modifier = Modifier.fillMaxSize())
  }

  detailEntry<AppSettingsRoute.StoriesRoute.GroupStory> { route ->
    Fragments.Fragment<GroupStorySettingsFragment>(
      modifier = Modifier.fillMaxSize(),
      arguments = GroupStorySettingsFragmentArgs.Builder(GroupId.parseOrThrow(route.groupId)).build().toBundle()
    )
  }

  detailEntry<AppSettingsRoute.StoriesRoute.PrivateStory> { route ->
    Fragments.Fragment<PrivateStorySettingsFragment>(
      modifier = Modifier.fillMaxSize(),
      arguments = PrivateStorySettingsFragmentArgs.Builder(DistributionListId.from(route.distributionListId)).build().toBundle()
    )
  }

  detailEntry<AppSettingsRoute.StoriesRoute.EditName> { route ->
    Fragments.Fragment<EditStoryNameFragment>(
      modifier = Modifier.fillMaxSize(),
      arguments = EditStoryNameFragmentArgs.Builder(DistributionListId.from(route.distributionListId), route.name).build().toBundle()
    )
  }

  detailEntry<AppSettingsRoute.StoriesRoute.AddViewers> { route ->
    Fragments.Fragment<AddViewersFragment>(
      modifier = Modifier.fillMaxSize(),
      arguments = AddViewersFragmentArgs.Builder(DistributionListId.from(route.distributionListId)).build().toBundle()
    )
  }
}
