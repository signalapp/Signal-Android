/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.compose

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.commitNow
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.FragmentState
import androidx.fragment.compose.rememberFragmentState
import androidx.navigation.fragment.NavHostFragment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.thoughtcrime.securesms.R

/**
 * Hosts a [NavHostFragment] inside Compose.
 *
 * [AndroidFragment] cannot host a [NavHostFragment] directly. It gives its container a
 * composition-derived id, and [NavHostFragment] reuses its own container id for the destinations it
 * navigates to. That id is written into the saved child-fragment state, so once the screen is
 * restored the child [androidx.fragment.app.FragmentManager] looks for a container id that no longer
 * exists and throws "No view found for id ...".
 *
 * Interposing [NavHostWrapperFragment] gives the nav host a container with a stable resource id,
 * which survives configuration changes and process death.
 */
@Composable
fun AndroidNavHostFragment(
  clazz: Class<out NavHostFragment>,
  modifier: Modifier = Modifier,
  fragmentState: FragmentState = rememberFragmentState(),
  arguments: Bundle = Bundle.EMPTY,
  onUpdate: (Fragment) -> Unit = {}
) {
  val wrapperArguments = remember(clazz, arguments) { NavHostWrapperFragment.createArgs(clazz, arguments) }

  AndroidFragment(
    clazz = NavHostWrapperFragment::class.java,
    modifier = modifier,
    fragmentState = fragmentState,
    arguments = wrapperArguments,
    onUpdate = onUpdate
  )
}

/**
 * Hosts a [NavHostFragment] in a container with a stable id. Use via [AndroidNavHostFragment].
 */
class NavHostWrapperFragment : Fragment(), FragmentBackPressedInfoProvider {

  companion object {
    private const val ARG_NAV_HOST_CLASS_NAME = "nav_host_wrapper.class_name"
    private const val ARG_NAV_HOST_ARGUMENTS = "nav_host_wrapper.arguments"

    fun createArgs(clazz: Class<out NavHostFragment>, arguments: Bundle): Bundle {
      return bundleOf(
        ARG_NAV_HOST_CLASS_NAME to clazz.name,
        ARG_NAV_HOST_ARGUMENTS to arguments
      )
    }
  }

  private val navHostFragment: NavHostFragment?
    get() = childFragmentManager.findFragmentById(R.id.nav_host_wrapper_container) as? NavHostFragment

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (navHostFragment != null) {
      return
    }

    val args = requireArguments()
    val className = requireNotNull(args.getString(ARG_NAV_HOST_CLASS_NAME)) { "No nav host class name in arguments." }
    val navHostClass = FragmentFactory.loadFragmentClass(requireContext().classLoader, className)

    childFragmentManager.commitNow {
      setReorderingAllowed(true)
      add(R.id.nav_host_wrapper_container, navHostClass, args.getBundle(ARG_NAV_HOST_ARGUMENTS))
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return FragmentContainerView(inflater.context).apply {
      id = R.id.nav_host_wrapper_container
      layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
  }

  override fun getFragmentBackPressedInfo(): Flow<FragmentBackPressedInfo> {
    val provider = navHostFragment as? FragmentBackPressedInfoProvider
    return provider?.getFragmentBackPressedInfo() ?: flowOf(FragmentBackPressedInfo.Disabled)
  }
}
