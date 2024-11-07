/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registrationv3.ui.permissions

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registration.fragments.WelcomePermissions
import org.thoughtcrime.securesms.registrationv3.ui.RegistrationCheckpoint
import org.thoughtcrime.securesms.registrationv3.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registrationv3.ui.welcome.WelcomeUserSelection
import org.thoughtcrime.securesms.util.BackupUtil

/**
 * Screen in account registration that provides rationales for the suggested runtime permissions.
 */
@RequiresApi(23)
class GrantPermissionsFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(GrantPermissionsFragment::class.java)

    const val REQUEST_KEY = "GrantPermissionsFragment"
  }

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val args by navArgs<GrantPermissionsFragmentArgs>()

  private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
    ::onPermissionsGranted
  )

  private val welcomeUserSelection: WelcomeUserSelection by lazy { args.welcomeUserSelection }

  @Composable
  override fun FragmentContent() {
    GrantPermissionsScreen(
      deviceBuildVersion = Build.VERSION.SDK_INT,
      isBackupSelectionRequired = BackupUtil.isUserSelectionRequired(LocalContext.current),
      onNextClicked = this::launchPermissionRequests,
      onNotNowClicked = this::proceedToNextScreen
    )
  }

  private fun launchPermissionRequests() {
    val isUserSelectionRequired = BackupUtil.isUserSelectionRequired(requireContext())

    val neededPermissions = WelcomePermissions.getWelcomePermissions(isUserSelectionRequired).filterNot {
      ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    if (neededPermissions.isEmpty()) {
      proceedToNextScreen()
    } else {
      requestPermissionLauncher.launch(neededPermissions.toTypedArray())
    }
  }

  private fun onPermissionsGranted(permissions: Map<String, Boolean>) {
    permissions.forEach {
      Log.d(TAG, "${it.key} = ${it.value}")
    }
    sharedViewModel.maybePrefillE164(requireContext())
    sharedViewModel.setRegistrationCheckpoint(RegistrationCheckpoint.PERMISSIONS_GRANTED)
    proceedToNextScreen()
  }

  private fun proceedToNextScreen() {
    setFragmentResult(REQUEST_KEY, bundleOf(REQUEST_KEY to welcomeUserSelection))
    findNavController().popBackStack()
  }
}
