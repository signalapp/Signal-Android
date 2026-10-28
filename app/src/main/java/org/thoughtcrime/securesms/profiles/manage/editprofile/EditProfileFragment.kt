/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.manage.editprofile

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.appsettings.editprofile.EditProfileEvent
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.appsettings.routes.AppSettingsRoute
import org.signal.appsettings.routes.AppSettingsRouter
import org.signal.core.models.media.Media
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.collectFlowOnViewLifecycle
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.picker.AvatarPickerFragment
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Fragment host for [EditProfileEntry] until edit profile moves to Nav3. Translates routes into the nav graph, and
 * results from the screens it opens into events.
 */
class EditProfileFragment : ComposeFragment() {

  private val appSettingsRouter by viewModels<AppSettingsRouter>()

  private val resultEvents = Channel<EditProfileEvent>(Channel.BUFFERED)
  private val resultEventFlow: Flow<EditProfileEvent> = resultEvents.receiveAsFlow()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    collectFlowOnViewLifecycle(appSettingsRouter.events, Lifecycle.State.RESUMED) { event ->
      when (event) {
        AppSettingsNavigationEvent.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
        is AppSettingsNavigationEvent.NavigateToScreen -> navigateToScreen(event.route)
      }
    }

    parentFragmentManager.setFragmentResultListener(AvatarPickerFragment.REQUEST_KEY_SELECT_AVATAR, viewLifecycleOwner) { _, bundle ->
      val media = bundle.getParcelableCompat(AvatarPickerFragment.SELECT_AVATAR_MEDIA, Media::class.java)
      when {
        bundle.getBoolean(AvatarPickerFragment.SELECT_AVATAR_CLEAR) -> resultEvents.trySend(EditProfileEvent.AvatarCleared)
        media != null -> resultEvents.trySend(EditProfileEvent.AvatarSelected(media))
      }
    }
  }

  private fun navigateToScreen(route: AppSettingsRoute) {
    when (route) {
      AppSettingsRoute.AccountRoute.ProfileName -> findNavController().safeNavigate(R.id.action_manageProfileName)
      AppSettingsRoute.AccountRoute.About -> findNavController().safeNavigate(R.id.action_manageAbout)
      is AppSettingsRoute.AccountRoute.Username -> findNavController().safeNavigate(R.id.action_manageUsername)
      AppSettingsRoute.AccountRoute.AvatarPicker -> findNavController().safeNavigate(EditProfileFragmentDirections.actionManageProfileFragmentToAvatarPicker(null, null))
      AppSettingsRoute.DonationsRoute.Badges -> findNavController().safeNavigate(R.id.action_manageProfileFragment_to_badgeManageFragment)
      AppSettingsRoute.UsernameLinkRoute.UsernameLink -> findNavController().safeNavigate(R.id.action_manageProfileFragment_to_usernameLinkFragment)
      else -> error("Unexpected route: ${route.javaClass.name}")
    }
  }

  @Composable
  override fun FragmentContent() {
    EditProfileEntry(
      parentEventEmitter = appSettingsRouter::onEvent,
      resultEvents = resultEventFlow
    )
  }
}
