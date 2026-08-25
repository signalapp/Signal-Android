/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import org.signal.appsettings.account.AccountSettingsAction
import org.signal.appsettings.account.AccountSettingsEvent
import org.signal.appsettings.account.AccountSettingsScreen
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.ServiceUtil
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.lock.v2.CreateSvrPinActivity
import org.thoughtcrime.securesms.registration.ui.RegistrationActivity
import org.thoughtcrime.securesms.util.PlayStoreUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Account settings shown on a primary device. Carries out the [AccountSettingsAction]s that need an Activity or the
 * legacy nav graph.
 */
class AccountSettingsFragment : ComposeFragment() {

  private val viewModel: AccountSettingsViewModel by viewModels()

  private lateinit var pinFlowLauncher: ActivityResultLauncher<Intent>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    pinFlowLauncher = registerForActivityResult(StartActivityForResult()) { result ->
      if (result.resultCode == Activity.RESULT_OK) {
        viewModel.onEvent(AccountSettingsEvent.PinCreated)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.onEvent(AccountSettingsEvent.ScreenResumed)
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CollectActions(viewModel.actions) { action -> handleAction(action) }

    AccountSettingsScreen(
      state = state,
      onEvent = viewModel::onEvent
    )
  }

  private fun handleAction(action: AccountSettingsAction) {
    when (action) {
      AccountSettingsAction.NavigateBack -> requireActivity().onBackPressedDispatcher.onBackPressed()
      AccountSettingsAction.LaunchCreatePinFlow -> pinFlowLauncher.launch(CreateSvrPinActivity.getIntentForPinCreate(requireContext()))
      AccountSettingsAction.LaunchChangePinFlow -> pinFlowLauncher.launch(CreateSvrPinActivity.getIntentForPinChangeFromSettings(requireContext()))
      AccountSettingsAction.ShowPinCreatedConfirmation -> Snackbar.make(requireView(), R.string.ConfirmKbsPinFragment__pin_created, Snackbar.LENGTH_LONG).show()
      AccountSettingsAction.NavigateToAuthenticatorAppSetup -> findNavController().safeNavigate(R.id.action_accountSettingsFragment_to_authenticatorSetupFragment)
      AccountSettingsAction.NavigateToAdvancedPinSettings -> findNavController().safeNavigate(R.id.action_accountSettingsFragment_to_advancedPinSettingsActivity)
      AccountSettingsAction.NavigateToChangePhoneNumber -> findNavController().safeNavigate(R.id.action_accountSettingsFragment_to_changePhoneNumberFragment)
      AccountSettingsAction.NavigateToDeviceTransfer -> findNavController().safeNavigate(R.id.action_accountSettingsFragment_to_oldDeviceTransferActivity)
      AccountSettingsAction.NavigateToExportAccountData -> findNavController().safeNavigate(R.id.action_accountSettingsFragment_to_exportAccountFragment)
      AccountSettingsAction.NavigateToDeleteAccount -> findNavController().safeNavigate(R.id.action_accountSettingsFragment_to_deleteAccountFragment)
      AccountSettingsAction.OpenPlayStore -> PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext())
      AccountSettingsAction.LaunchReRegistration -> startActivity(RegistrationActivity.newIntentForReRegistration(requireContext()))
      AccountSettingsAction.WipeAllData -> {
        if (!ServiceUtil.getActivityManager(AppDependencies.application).clearApplicationUserData()) {
          viewModel.onEvent(AccountSettingsEvent.DataWipeFailed)
        }
      }
      AccountSettingsAction.ShowDataWipeFailed -> toast(R.string.preferences_account_delete_all_data_failed)
      AccountSettingsAction.ShowRegistrationLockEnableFailed -> toast(R.string.preferences_app_protection__failed_to_enable_registration_lock)
      AccountSettingsAction.ShowRegistrationLockDisableFailed -> toast(R.string.preferences_app_protection__failed_to_disable_registration_lock)
    }
  }

  private fun toast(@StringRes message: Int) {
    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
  }
}
