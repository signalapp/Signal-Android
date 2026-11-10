/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.compose

import android.os.Bundle
import androidx.annotation.NavigationRes
import androidx.core.os.bundleOf
import androidx.navigation.fragment.NavHostFragment
import kotlinx.coroutines.flow.Flow

/**
 * Hosts a nav2 graph, given by [createArgs], within a nav3 entry, handling back presses itself while its own back stack
 * has something to pop.
 */
class GraphNavHostFragment : NavHostFragment(), FragmentBackPressedInfoProvider {

  companion object {
    private const val ARG_GRAPH_ID = "graph_nav_host.graph_id"
    private const val ARG_START_ARGS = "graph_nav_host.start_args"

    fun createArgs(@NavigationRes graphId: Int, startArgs: Bundle? = null): Bundle {
      return bundleOf(
        ARG_GRAPH_ID to graphId,
        ARG_START_ARGS to startArgs
      )
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    val args = requireArguments()
    navController.setGraph(args.getInt(ARG_GRAPH_ID), args.getBundle(ARG_START_ARGS))
    super.onCreate(savedInstanceState)
  }

  override fun getFragmentBackPressedInfo(): Flow<FragmentBackPressedInfo> = navController.fragmentBackPressedInfo()
}
