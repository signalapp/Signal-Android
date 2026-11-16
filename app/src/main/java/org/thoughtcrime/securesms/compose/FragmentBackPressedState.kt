/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.compose

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Allows us to support view based fragments hosted in a compose-based activity having their
 * own back handling.
 */
@Stable
class FragmentBackPressedState @RememberInComposition constructor() {
  var info by mutableStateOf<FragmentBackPressedInfo?>(null)

  fun attach(fragment: Fragment) {
    if (fragment is FragmentBackPressedInfoProvider) {
      with(fragment) {
        viewLifecycleOwner.lifecycleScope.launch {
          repeatOnLifecycle(state = Lifecycle.State.CREATED) {
            getFragmentBackPressedInfo().collect {
              info = it
            }
          }
        }
      }
    }
  }
}

/**
 * Describes the current back-pressed state, produced by a [Fragment]
 */
sealed interface FragmentBackPressedInfo {
  object Disabled : FragmentBackPressedInfo
  data class Enabled(val callback: () -> Unit) : FragmentBackPressedInfo
}

/**
 * Fragment should implement this interface.
 */
interface FragmentBackPressedInfoProvider {
  fun getFragmentBackPressedInfo(): Flow<FragmentBackPressedInfo>
}

/**
 * BackHandler for interop with legacy style fragments.
 * Don't forget to call [FragmentBackPressedState.attach]!
 */
@Composable
fun FragmentBackHandler(state: FragmentBackPressedState) {
  val info = state.info
  val enabled = when (info) {
    FragmentBackPressedInfo.Disabled -> false
    is FragmentBackPressedInfo.Enabled -> true
    null -> false
  }

  BackHandler(enabled = enabled) {
    if (info is FragmentBackPressedInfo.Enabled) {
      info.callback()
    }
  }
}

/**
 * Lets a nav host fragment's own back stack handle back presses while it has something to pop, and leaves them to the
 * host otherwise.
 */
fun NavController.fragmentBackPressedInfo(): Flow<FragmentBackPressedInfo> {
  return currentBackStackEntryFlow.map {
    if (previousBackStackEntry != null) {
      FragmentBackPressedInfo.Enabled { popBackStack() }
    } else {
      FragmentBackPressedInfo.Disabled
    }
  }
}
