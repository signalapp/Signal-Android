/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.backup.v2.ui.subscription

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlowable
import org.signal.core.ui.compose.CollectActions
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.Dialogs
import org.signal.core.util.Result
import org.signal.core.util.Util
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.getSerializableCompat
import org.signal.passwordmanager.SignalCredentialManager
import org.signal.signallogin.pdf.SignalLoginPdfRenderer
import org.signal.signallogin.viewdetails.SignalLoginViewDetailsScreen
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.DeletionState
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.components.settings.app.account.signallogin.SignalLoginViewDetailsAction
import org.thoughtcrime.securesms.components.settings.app.account.signallogin.SignalLoginViewDetailsViewModel
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.InAppPaymentCheckoutDelegate
import org.thoughtcrime.securesms.compose.Nav
import org.thoughtcrime.securesms.database.InAppPaymentTable
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.PlayStoreUtil
import org.thoughtcrime.securesms.util.viewModel

/**
 * Handles the selection, payment, and changing of a user's backup tier.
 */
class MessageBackupsFlowFragment : ComposeFragment(), InAppPaymentCheckoutDelegate.ErrorHandlerCallback {

  companion object {

    @VisibleForTesting
    const val TIER = "tier"
    const val CLIPBOARD_TIMEOUT_SECONDS = 60

    private const val PDF_MIME_TYPE = "application/pdf"

    fun create(messageBackupTier: MessageBackupTier?): MessageBackupsFlowFragment {
      return MessageBackupsFlowFragment().apply {
        arguments = bundleOf(TIER to messageBackupTier)
      }
    }
  }

  private val viewModel: MessageBackupsFlowViewModel by viewModel {
    MessageBackupsFlowViewModel(
      initialTierSelection = requireArguments().getSerializableCompat(TIER, MessageBackupTier::class.java),
      googlePlayApiAvailability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireContext()),
      isCredentialManagerSupported = SignalCredentialManager.isSupported(requireContext()),
      isPhoneNumberless = SignalStore.account.isPhoneNumberless
    )
  }

  private val signalLoginViewDetailsViewModel: SignalLoginViewDetailsViewModel by viewModels()

  private val savePdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument(PDF_MIME_TYPE)) { uri: Uri? ->
    if (uri != null) {
      val context = requireContext().applicationContext
      lifecycleScope.launch {
        val result = SignalLoginPdfRenderer.renderTo(context, uri, signalLoginViewDetailsViewModel.state.value.accountKey, signalLoginViewDetailsViewModel.state.value.recoveryKeyGroups)
        if (result is Result.Failure) {
          Toast.makeText(context, result.failure.userMessageRes, Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  private val errorHandler = InAppPaymentCheckoutDelegate.ErrorHandler()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    errorHandler.attach(
      fragment = this,
      errorHandlerCallback = this,
      inAppPaymentIdSource = viewModel.stateFlow.asFlowable()
        .filter { it.inAppPayment != null }
        .map { it.inAppPayment!!.id }
    )

    viewLifecycleOwner.lifecycleScope.launch(SignalDispatchers.Main) {
      repeatOnLifecycle(Lifecycle.State.RESUMED) {
        viewModel.deletionState.collectLatest {
          if (it == DeletionState.DELETE_BACKUPS) {
            Toast.makeText(
              requireContext(),
              R.string.MessageBackupsFlowFragment__a_backup_deletion_is_in_progress,
              Toast.LENGTH_SHORT
            ).show()

            requireActivity().supportFinishAfterTransition()
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshCurrentTier()
    viewModel.setGooglePlayApiAvailability(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireContext()))
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
      navController.setLifecycleOwner(this@MessageBackupsFlowFragment)

      requireActivity().onBackPressedDispatcher.addCallback(
        lifecycleOwner,
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            viewModel.goToPreviousStage()
          }
        }
      )
    }

    Nav.Host(
      navController = navController,
      startDestination = state.startScreen.name
    ) {
      composable(route = MessageBackupsStage.Route.EDUCATION.name) {
        MessageBackupsEducationScreen(
          onNavigationClick = viewModel::goToPreviousStage,
          onEnableBackups = viewModel::goToNextStage,
          onNotNow = viewModel::goToPreviousStage
        )
      }

      composable(route = MessageBackupsStage.Route.BACKUP_KEY_EDUCATION.name) {
        MessageBackupsKeyEducationScreen(
          onNavigationClick = viewModel::goToPreviousStage,
          onNextClick = viewModel::goToNextStage,
          mode = if (SignalStore.backup.newLocalBackupsEnabled) {
            MessageBackupsKeyEducationScreenMode.REMOTE_WITH_LOCAL_ENABLED
          } else {
            MessageBackupsKeyEducationScreenMode.DEFAULT
          }
        )
      }

      composable(route = MessageBackupsStage.Route.BACKUP_KEY_RECORD.name) {
        val context = LocalContext.current
        val passwordManagerSettingsIntent = SignalCredentialManager.getSettingsIntent(requireContext())

        MessageBackupsKeyRecordScreen(
          backupKey = state.accountEntropyPool.displayValue,
          keySaveState = state.backupKeySaveState,
          canOpenPasswordManagerSettings = passwordManagerSettingsIntent != null,
          onNavigationClick = viewModel::goToPreviousStage,
          mode = remember {
            MessageBackupsKeyRecordMode.Passkey(
              onSaveToPasswordManager = viewModel::onBackupKeySaveRequested,
              onSaveManually = viewModel::goToRecordManually,
              onSaveSuccessful = viewModel::onBackupKeySavedToPasswordManager
            )
          },
          onCopyToClipboardClick = { Util.copyToClipboard(context, it, CLIPBOARD_TIMEOUT_SECONDS) },
          onRequestSaveToPasswordManager = viewModel::onBackupKeySaveRequested,
          onConfirmSaveToPasswordManager = viewModel::onBackupKeySaveConfirmed,
          onSaveStateCleared = viewModel::onBackupKeySaveStateCleared,
          onSaveToPasswordManagerComplete = viewModel::onBackupKeySaveCompleted,
          onGoToPasswordManagerSettingsClick = { requireContext().startActivity(passwordManagerSettingsIntent) },
          notifyKeyIsSameAsOnDeviceBackupKey = SignalStore.backup.newLocalBackupsEnabled
        )
      }

      composable(route = MessageBackupsStage.Route.BACKUP_KEY_RECORD_MANUALLY.name) {
        val context = LocalContext.current
        val passwordManagerSettingsIntent = SignalCredentialManager.getSettingsIntent(requireContext())

        MessageBackupsKeyRecordScreen(
          backupKey = state.accountEntropyPool.displayValue,
          keySaveState = state.backupKeySaveState,
          canOpenPasswordManagerSettings = passwordManagerSettingsIntent != null,
          onNavigationClick = viewModel::goToPreviousStage,
          mode = remember { MessageBackupsKeyRecordMode.Next(viewModel::goToNextStage) },
          onCopyToClipboardClick = { Util.copyToClipboard(context, it, CLIPBOARD_TIMEOUT_SECONDS) },
          onRequestSaveToPasswordManager = viewModel::onBackupKeySaveRequested,
          onConfirmSaveToPasswordManager = viewModel::onBackupKeySaveConfirmed,
          onSaveToPasswordManagerComplete = viewModel::onBackupKeySaveCompleted,
          onGoToPasswordManagerSettingsClick = { requireContext().startActivity(passwordManagerSettingsIntent) },
          notifyKeyIsSameAsOnDeviceBackupKey = SignalStore.backup.newLocalBackupsEnabled
        )
      }

      composable(route = MessageBackupsStage.Route.BACKUP_KEY_VERIFY.name) {
        MessageBackupsKeyVerifyScreen(
          backupKey = state.accountEntropyPool.displayValue,
          onNavigationClick = viewModel::goToPreviousStage,
          onNextClick = viewModel::goToNextStage,
          mode = if (state.isPhoneNumberless) {
            MessageBackupsKeyVerifyScreenMode.SIGNAL_LOGIN
          } else {
            MessageBackupsKeyVerifyScreenMode.DEFAULT
          }
        )
      }

      composable(route = MessageBackupsStage.Route.CONFIRM_RECOVERY_KEY.name) {
        MessageBackupsConfirmRecoveryKeyScreen(
          aci = state.aci,
          aep = state.accountEntropyPool,
          onNavigationClick = viewModel::goToPreviousStage,
          onViewDetailsClick = viewModel::goToSignalLoginViewDetails,
          onConfirmed = viewModel::onRecoveryKeyConfirmed,
          onEnterManuallyClick = viewModel::goToEnterRecoveryKeyManually
        )
      }

      composable(route = MessageBackupsStage.Route.SIGNAL_LOGIN_VIEW_DETAILS.name) {
        val signalLoginState by signalLoginViewDetailsViewModel.state.collectAsStateWithLifecycle()

        CollectActions(signalLoginViewDetailsViewModel.actions) { action -> handleSignalLoginViewDetailsAction(action) }

        SignalLoginViewDetailsScreen(
          state = signalLoginState,
          onEvent = signalLoginViewDetailsViewModel::onEvent
        )
      }

      composable(route = MessageBackupsStage.Route.TYPE_SELECTION.name) {
        MessageBackupsTypeSelectionScreen(
          stage = state.stage,
          currentBackupTier = state.currentMessageBackupTier,
          selectedBackupTier = state.selectedMessageBackupTier,
          allBackupTypes = state.allBackupTypes,
          isNextEnabled = state.isCheckoutButtonEnabled(),
          onMessageBackupsTierSelected = viewModel::onMessageBackupTierUpdated,
          onNavigationClick = viewModel::goToPreviousStage,
          onReadMoreClicked = {
            CommunicationActions.openBrowserLink(
              requireContext(),
              getString(R.string.remote_backup_support_url)
            )
          },
          onNextClicked = viewModel::goToNextStage,
          googlePlayServicesAvailability = state.googlePlayApiAvailability,
          googlePlayBillingAvailability = state.googlePlayBillingAvailability,
          onLearnMoreAboutWhyUserCanNotUpgrade = {
            CommunicationActions.openBrowserLink(
              requireContext(),
              getString(R.string.remote_backup_support_url)
            )
          },
          onMakeGooglePlayServicesAvailable = {
            GoogleApiAvailability.getInstance().makeGooglePlayServicesAvailable(requireActivity()).addOnSuccessListener {
              viewModel.setGooglePlayApiAvailability(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireContext()))
            }
          },
          onOpenPlayStore = {
            PlayStoreUtil.openPlayStoreHome(requireContext())
          }
        )
      }
    }

    LaunchedEffect(state.stage) {
      val newRoute = state.stage.route.name
      val currentRoute = navController.currentDestination?.route
      if (currentRoute != newRoute) {
        if (currentRoute != null && MessageBackupsStage.Route.valueOf(currentRoute).isAfter(state.stage.route)) {
          navController.popBackStack(newRoute, inclusive = false)
        } else {
          navController.navigate(newRoute)
        }
      }

      when (state.stage) {
        MessageBackupsStage.CANCEL -> requireActivity().finishAfterTransition()
        MessageBackupsStage.CHECKOUT_SHEET -> AppDependencies.billingApi.launchBillingFlow(requireActivity())
        MessageBackupsStage.COMPLETED -> {
          requireActivity().setResult(Activity.RESULT_OK, MessageBackupsCheckoutActivity.createResultData())
          requireActivity().finishAfterTransition()
        }

        else -> Unit
      }
    }

    if (state.paymentReadyState == MessageBackupsFlowState.PaymentReadyState.FAILED) {
      Dialogs.SimpleMessageDialog(
        message = stringResource(R.string.MessageBackupsFlowFragment__a_network_failure_occurred),
        dismiss = stringResource(android.R.string.ok),
        onDismiss = { requireActivity().finishAfterTransition() }
      )
    }
  }

  private fun handleSignalLoginViewDetailsAction(action: SignalLoginViewDetailsAction) {
    when (action) {
      SignalLoginViewDetailsAction.NavigateBack -> viewModel.goToPreviousStage()
      SignalLoginViewDetailsAction.LaunchSaveToPasswordManager -> {
        lifecycleScope.launch {
          SignalCredentialManager.saveCredential(
            activityContext = requireActivity(),
            username = signalLoginViewDetailsViewModel.state.value.accountKey,
            password = signalLoginViewDetailsViewModel.state.value.recoveryKey
          )
        }
      }
      SignalLoginViewDetailsAction.LaunchSaveAsPdf -> savePdfLauncher.launch(SignalLoginPdfRenderer.suggestedFileName(requireContext()))
      is SignalLoginViewDetailsAction.CopyTextToClipboard -> Util.copyToClipboard(requireContext(), action.text, CLIPBOARD_TIMEOUT_SECONDS)
    }
  }

  override fun onUserLaunchedAnExternalApplication() = error("Not supported by this fragment.")

  override fun navigateToDonationPending(inAppPayment: InAppPaymentTable.InAppPayment) = error("Not supported by this fragment.")
  override fun exitCheckoutFlow() {
    requireActivity().finishAfterTransition()
  }
}
