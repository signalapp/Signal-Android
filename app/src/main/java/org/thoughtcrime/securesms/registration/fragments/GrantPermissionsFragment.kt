/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.fragments

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.registration.compose.GrantPermissionsScreen
import org.thoughtcrime.securesms.registration.viewmodel.RegistrationViewModel
import org.thoughtcrime.securesms.util.BackupUtil

/**
 * Fragment displayed during registration which allows a user to read through
 * what permissions are granted to Signal and why, and a means to either skip
 * granting those permissions or continue to grant via system dialogs.
 */
class GrantPermissionsFragment : ComposeFragment() {

  private val args by navArgs<GrantPermissionsFragmentArgs>()
  private val viewModel by activityViewModels<RegistrationViewModel>()
  private val isSearchingForBackup = mutableStateOf(false)

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

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  private fun onNextClicked() {
    when (args.welcomeAction) {
      WelcomeAction.CONTINUE -> {
        WelcomeFragment.continueClicked(
          this,
          viewModel,
          { isSearchingForBackup.value = true },
          { isSearchingForBackup.value = false },
          GrantPermissionsFragmentDirections.actionSkipRestore(),
          GrantPermissionsFragmentDirections.actionRestore()
        )
      }

      WelcomeAction.RESTORE_BACKUP -> {
        WelcomeFragment.restoreFromBackupClicked(
          this,
          viewModel,
          GrantPermissionsFragmentDirections.actionTransferOrRestore()
        )
      }
    }
  }

  private fun onNotNowClicked() {
    when (args.welcomeAction) {
      WelcomeAction.CONTINUE -> {
        WelcomeFragment.gatherInformationAndContinue(
          this,
          viewModel,
          { isSearchingForBackup.value = true },
          { isSearchingForBackup.value = false },
          GrantPermissionsFragmentDirections.actionSkipRestore(),
          GrantPermissionsFragmentDirections.actionRestore()
        )
      }

      WelcomeAction.RESTORE_BACKUP -> {
        WelcomeFragment.gatherInformationAndChooseBackup(
          this,
          viewModel,
          GrantPermissionsFragmentDirections.actionTransferOrRestore()
        )
      }
    }
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
