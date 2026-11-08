/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.listdetail.demo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import org.signal.core.ui.compose.split.ListDetailEvents
import org.signal.core.ui.navigation.TransitionSpecs

/**
 * Settings' own nav keys. The stack outside knows about settings as a single entry; these are the screens
 * within it, and nothing outside needs to know they exist.
 */
@Serializable
private sealed interface SettingsRoute : NavKey {

  @Serializable
  data object Root : SettingsRoute

  @Serializable
  data class Section(val title: String) : SettingsRoute
}

private val SECTIONS = listOf("Notifications", "Privacy", "Storage")

/**
 * A section that takes the whole window and navigates for itself, which is what a plain `entry` gets you:
 * no list pane, no divider, no empty detail — just this, over the panes it was pushed on top of.
 *
 * Its back stack is a [rememberNavBackStack], so the entry decorators hold it while the user is off in
 * another tab and hand it back with settings still open on whatever screen they left it.
 *
 * @param onEvent reports back out to the demo, which is how settings gets popped off the stack outside
 *   once this display is at its own root.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onEvent: (DemoEvents) -> Unit, modifier: Modifier = Modifier) {
  val backStack = rememberNavBackStack(SettingsRoute.Root)

  val paneShift = TransitionSpecs.paneShift()
  val paneShiftPop = TransitionSpecs.paneShift(pop = true)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    transitionSpec = { paneShift },
    popTransitionSpec = { paneShiftPop },
    predictivePopTransitionSpec = { paneShiftPop },
    modifier = modifier.fillMaxSize(),
    entryProvider = entryProvider {
      entry<SettingsRoute.Root> {
        SettingsScaffold(title = "Settings", onBack = { onEvent(DemoEvents.ListDetailEvent(ListDetailEvents.Back)) }) {
          SECTIONS.forEach { section ->
            SettingsRow(title = section, onClick = { backStack.add(SettingsRoute.Section(section)) })
          }
        }
      }

      entry<SettingsRoute.Section> { route ->
        SettingsScaffold(title = route.title, onBack = { backStack.removeLastOrNull() }) {
          Text(
            text = "Back here returns to settings, not to the list underneath.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
          )
        }
      }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(
  title: String,
  onBack: () -> Unit,
  content: @Composable () -> Unit
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = title) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        }
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      content()
    }
  }
}

@Composable
private fun SettingsRow(title: String, onClick: () -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 24.dp, vertical = 20.dp)
  ) {
    Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))

    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
