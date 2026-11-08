/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.stories

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.rememberFragmentState
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.signal.core.ui.compose.split.detailEntry
import org.signal.core.ui.navigation.TransitionSpecs
import org.thoughtcrime.securesms.MainNavigator
import org.thoughtcrime.securesms.compose.FragmentBackHandler
import org.thoughtcrime.securesms.compose.FragmentBackPressedState
import org.thoughtcrime.securesms.main.MainDetailRoute
import org.thoughtcrime.securesms.stories.archive.StoryArchiveScreen
import org.thoughtcrime.securesms.stories.landing.StoriesLandingFragment
import org.thoughtcrime.securesms.stories.my.MyStoriesFragment
import org.thoughtcrime.securesms.stories.settings.StorySettingsNavHostFragment

fun EntryProviderScope<NavKey>.registerStoriesTabDetailRoutes() {
  detailEntry<MainDetailRoute.Stories.Archive>(
    metadata = TransitionSpecs.None.metadata
  ) {
    StoryArchiveEntry()
  }

  detailEntry<MainDetailRoute.Stories.MyStories>(
    metadata = TransitionSpecs.None.metadata
  ) {
    MyStoriesEntry()
  }

  detailEntry<MainDetailRoute.Stories.PrivacySettings>(
    metadata = TransitionSpecs.None.metadata
  ) {
    StoryPrivacySettingsEntry()
  }
}

/**
 * List pane content for the stories tab.
 */
@Composable
fun StoriesListPane(modifier: Modifier = Modifier) {
  AndroidFragment(
    clazz = StoriesLandingFragment::class.java,
    fragmentState = rememberFragmentState(),
    modifier = modifier
  )
}

@Composable
private fun StoryArchiveEntry() {
  val backPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
  informNavigatorWeAreReady()

  StoryArchiveScreen(
    onNavigationClick = { backPressedDispatcherOwner?.onBackPressedDispatcher?.onBackPressed() }
  )
}

@Composable
private fun MyStoriesEntry() {
  val fragmentState = key(MainDetailRoute.Stories.MyStories) { rememberFragmentState() }

  informNavigatorWeAreReady()

  // No FragmentBackHandler: the fragment has no back state of its own, so back belongs to the display
  // that put this entry on the stack.
  AndroidFragment(
    clazz = MyStoriesFragment::class.java,
    fragmentState = fragmentState,
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .statusBarsPadding()
      .navigationBarsPadding()
  )
}

@Composable
private fun StoryPrivacySettingsEntry() {
  val fragmentState = key(MainDetailRoute.Stories.PrivacySettings) { rememberFragmentState() }
  val backPressedState = remember { FragmentBackPressedState() }
  FragmentBackHandler(backPressedState)

  informNavigatorWeAreReady()

  AndroidFragment(
    clazz = StorySettingsNavHostFragment::class.java,
    fragmentState = fragmentState,
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .statusBarsPadding()
      .navigationBarsPadding()
  ) { fragment ->
    backPressedState.attach(fragment)
  }
}

@Composable
private fun informNavigatorWeAreReady() {
  val navigator = LocalActivity.current as? MainNavigator.NavigatorProvider
  LaunchedEffect(navigator) {
    navigator?.onFirstRender()
  }
}
