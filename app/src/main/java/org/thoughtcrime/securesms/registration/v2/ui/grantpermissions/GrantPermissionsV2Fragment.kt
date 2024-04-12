/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.v2.ui.grantpermissions

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.navArgs
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.registration.compose.GrantPermissionsScreen
import org.thoughtcrime.securesms.registration.fragments.WelcomePermissions
import org.thoughtcrime.securesms.registration.v2.ui.shared.RegistrationCheckpoint
import org.thoughtcrime.securesms.registration.v2.ui.shared.RegistrationV2State
import org.thoughtcrime.securesms.registration.v2.ui.shared.RegistrationV2ViewModel
import org.thoughtcrime.securesms.util.BackupUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Screen in account registration that provides rationales for the suggested runtime permissions.
 */
@RequiresApi(23)
class GrantPermissionsV2Fragment : ComposeFragment() {

  private val TAG = Log.tag(GrantPermissionsV2Fragment::class.java)

  private val sharedViewModel by activityViewModels<RegistrationV2ViewModel>()
  private val args by navArgs<GrantPermissionsV2FragmentArgs>()
  private val isSearchingForBackup = mutableStateOf(false)

  private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
    ::permissionsGranted
  )

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    sharedViewModel.uiState.observe(viewLifecycleOwner) {
      if (it.registrationCheckpoint >= RegistrationCheckpoint.PERMISSIONS_GRANTED) {
        proceedToNextScreen(it)
      }
    }
  }

  private fun proceedToNextScreen(it: RegistrationV2State) {
    // TODO [nicholas]: conditionally go to backup flow
    NavHostFragment.findNavController(this).safeNavigate(GrantPermissionsV2FragmentDirections.actionSkipRestore())
  }

  @Composable
  override fun FragmentContent() {
    val isSearchingForBackup by this.isSearchingForBackup

    GrantPermissionsScreen(
      deviceBuildVersion = Build.VERSION.SDK_INT,
      isSearchingForBackup = isSearchingForBackup,
      isBackupSelectionRequired = BackupUtil.isUserSelectionRequired(LocalContext.current),
      onNextClicked = this::onNextClicked,
      onNotNowClicked = this::onNotNowClicked
    )
  }

  private fun onNextClicked() {
    when (args.welcomeAction) {
      WelcomeAction.CONTINUE -> continueNext()
      WelcomeAction.RESTORE_BACKUP -> Log.w(TAG, "Not yet implemented!", NotImplementedError()) // TODO [regv2]
    }
  }

  private fun continueNext() {
    val isUserSelectionRequired = BackupUtil.isUserSelectionRequired(requireContext())
    val requiredPermissions = WelcomePermissions.getWelcomePermissions(isUserSelectionRequired)
    requestPermissionLauncher.launch(requiredPermissions)
  }

  private fun onNotNowClicked() {
    when (args.welcomeAction) {
      WelcomeAction.CONTINUE -> continueNotNow()
      WelcomeAction.RESTORE_BACKUP -> Log.w(TAG, "Not yet implemented!", NotImplementedError()) // TODO [regv2]
    }
  }

  private fun continueNotNow() {
    NavHostFragment.findNavController(this).popBackStack()
  }

  private fun permissionsGranted(permissions: Map<String, Boolean>) {
    permissions.forEach {
      Log.d(TAG, "${it.key} = ${it.value}")
    }
    sharedViewModel.setRegistrationCheckpoint(RegistrationCheckpoint.PERMISSIONS_GRANTED)
  }

  /**
   * Which welcome action the user selected which prompted this
   * screen.
   */
  enum class WelcomeAction {
    CONTINUE,
    RESTORE_BACKUP
  }
}
