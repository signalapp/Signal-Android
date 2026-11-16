/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.avatar.picker

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.navigation.NavRouter

/**
 * Hosts [AvatarPickerNavigation] for flows that are still fragment-based, such as creating a profile or a group, and
 * reports what the user picked as a fragment result under [REQUEST_KEY_SELECT_AVATAR].
 */
class AvatarPickerFragment : ComposeFragment() {

  companion object {
    const val REQUEST_KEY_SELECT_AVATAR = "org.thoughtcrime.securesms.avatar.picker.SELECT_AVATAR"
    const val SELECT_AVATAR_MEDIA = "org.thoughtcrime.securesms.avatar.picker.SELECT_AVATAR_MEDIA"
    const val SELECT_AVATAR_CLEAR = "org.thoughtcrime.securesms.avatar.picker.SELECT_AVATAR_CLEAR"
  }

  private val router: NavRouter<AppSettingsRoute> by viewModels {
    viewModelFactory {
      initializer { NavRouter<AppSettingsRoute>(createSavedStateHandle(), AppSettingsRoute.AccountRoute.AvatarPicker) }
    }
  }

  @Composable
  override fun FragmentContent() {
    val viewModelFactory = remember {
      val args = AvatarPickerFragmentArgs.fromBundle(requireArguments())
      AvatarPickerViewModel.Factory(args.groupId, args.isNewGroup, args.groupAvatarMedia)
    }

    AvatarPickerNavigation(
      router = router,
      viewModelFactory = viewModelFactory,
      onResult = { result ->
        val bundle = when (result) {
          is AvatarPickerResult.Selected -> Bundle().apply { putParcelable(SELECT_AVATAR_MEDIA, result.media) }
          AvatarPickerResult.Cleared -> Bundle().apply { putBoolean(SELECT_AVATAR_CLEAR, true) }
        }
        setFragmentResult(REQUEST_KEY_SELECT_AVATAR, bundle)
      },
      onClose = { findNavController().popBackStack() }
    )
  }
}
