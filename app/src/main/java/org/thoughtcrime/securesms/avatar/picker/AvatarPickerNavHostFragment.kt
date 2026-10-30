/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.picker

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.core.os.bundleOf
import androidx.fragment.compose.rememberFragmentState
import androidx.navigation.fragment.NavHostFragment
import kotlinx.coroutines.flow.Flow
import org.signal.core.models.media.Media
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.AndroidNavHostFragment
import org.thoughtcrime.securesms.compose.FragmentBackHandler
import org.thoughtcrime.securesms.compose.FragmentBackPressedInfo
import org.thoughtcrime.securesms.compose.FragmentBackPressedInfoProvider
import org.thoughtcrime.securesms.compose.FragmentBackPressedState
import org.thoughtcrime.securesms.compose.NavHostWrapperFragment
import org.thoughtcrime.securesms.compose.fragmentBackPressedInfo

/** What the user chose in the avatar picker. */
sealed interface AvatarPickerResult {
  data class Selected(val media: Media) : AvatarPickerResult {
    override fun toString(): String = "Selected"
  }

  data object Cleared : AvatarPickerResult
}

/**
 * Embeds the fragment-based avatar picker, and its text and vector editors, until they move to Nav3. The picker exits
 * as a back press, which the host's nav display handles.
 *
 * @param onResult called with what the user picked, just before the picker exits.
 */
@Composable
fun AvatarPickerEntry(onResult: (AvatarPickerResult) -> Unit) {
  val currentOnResult by rememberUpdatedState(onResult)
  val fragmentState = rememberFragmentState()
  val backPressedState = remember { FragmentBackPressedState() }
  val arguments = remember { AvatarPickerNavHostFragment.createArgs() }

  FragmentBackHandler(backPressedState)

  AndroidNavHostFragment(
    clazz = AvatarPickerNavHostFragment::class.java,
    fragmentState = fragmentState,
    arguments = arguments,
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) { wrapper ->
    backPressedState.attach(wrapper)

    (wrapper as NavHostWrapperFragment).navHostFragment?.let { navHost ->
      (navHost as AvatarPickerNavHostFragment).onResult = { result -> currentOnResult(result) }
    }
  }
}

/**
 * Hosts the `avatar_picker` graph for [AvatarPickerEntry], reporting the picker's fragment result through [onResult].
 */
class AvatarPickerNavHostFragment : NavHostFragment(), FragmentBackPressedInfoProvider {

  companion object {
    private const val ARG_START_BUNDLE = "avatar_picker_nav_host.start_bundle"

    fun createArgs(): Bundle {
      return bundleOf(ARG_START_BUNDLE to AvatarPickerFragmentArgs.Builder(null, null).build().toBundle())
    }
  }

  /** Called with what the user picked, just before the picker exits. */
  var onResult: ((AvatarPickerResult) -> Unit)? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    navController.setGraph(R.navigation.avatar_picker, requireArguments().getBundle(ARG_START_BUNDLE))
    super.onCreate(savedInstanceState)

    childFragmentManager.setFragmentResultListener(AvatarPickerFragment.REQUEST_KEY_SELECT_AVATAR, this) { _, bundle ->
      val media = bundle.getParcelableCompat(AvatarPickerFragment.SELECT_AVATAR_MEDIA, Media::class.java)
      when {
        bundle.getBoolean(AvatarPickerFragment.SELECT_AVATAR_CLEAR) -> onResult?.invoke(AvatarPickerResult.Cleared)
        media != null -> onResult?.invoke(AvatarPickerResult.Selected(media))
      }
    }
  }

  override fun getFragmentBackPressedInfo(): Flow<FragmentBackPressedInfo> = navController.fragmentBackPressedInfo()
}
