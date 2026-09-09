/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.app.Application
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.signal.archive.LocalBackupRestoreProgress
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.MasterKey
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.ServiceId.PNI
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.Base64
import org.signal.core.util.billing.OneTimePurchaseApi
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.network.api.RegistrationApiV2.CheckSvrCredentialsResponse
import org.signal.network.api.RegistrationApiV2.CreateLoginReceiptCredentialResult
import org.signal.network.api.RegistrationApiV2.RegisterAccountError
import org.signal.network.api.RegistrationApiV2.RegistrationLockResponse
import org.signal.network.api.RegistrationApiV2.RestoreMethod
import org.signal.network.api.RegistrationApiV2.SvrCredentials
import org.signal.network.api.RegistrationApiV2.UpdateSessionError
import org.signal.passwordmanager.CredentialManagerResult
import org.signal.passwordmanager.SignalCredentialManager
import org.signal.passwordmanager.UsernamePasswordCredential
import org.signal.registration.NetworkController.MasterKeyResponse
import org.signal.registration.NetworkController.ProvisioningEvent
import org.signal.registration.NetworkController.RestoreMasterKeyError
import org.signal.registration.fakes.FakeNetworkController
import org.signal.registration.fakes.FakeOneTimePurchaseApi
import org.signal.registration.fakes.FakeStorageController
import org.signal.registration.fakes.SystemOutLogger
import org.signal.registration.proto.SvrCredential
import org.signal.registration.screens.remotebackuprestore.RemoteBackupRestoreProgress
import org.signal.registration.screens.util.MockMultiplePermissionsState
import org.signal.registration.screens.util.MockPermissionsState
import org.signal.registration.test.TestTags
import java.time.Duration
import java.util.UUID
import kotlin.time.Duration.Companion.days

/**
 * End-to-end tests for the registration flow: renders the full [RegistrationNavHost] with a real
 * [RegistrationRepository] backed by in-memory fake controllers, and drives it by interacting with
 * the UI the way a user would.
 *
 * The fakes default to a happy path. To exercise other navigation paths, override the relevant
 * response handler on [networkController] or state on [storageController] before driving the UI.
 */
@OptIn(ExperimentalPermissionsApi::class, InternalComposeUiApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RegistrationEndToEndTest {

  companion object {
    private const val PHONE_NUMBER = "5550123456"
    private const val E164 = "+1$PHONE_NUMBER"
    private const val VERIFICATION_CODE = FakeNetworkController.DEFAULT_VERIFICATION_CODE
    private const val PIN = "9182"
    private const val USERNAME = "signaluser"
    private const val WAIT_TIMEOUT_MS = 30_000L
  }

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(ApplicationProvider.getApplicationContext())

  private lateinit var networkController: FakeNetworkController
  private lateinit var storageController: FakeStorageController
  private lateinit var purchaseApi: FakeOneTimePurchaseApi
  private lateinit var repository: RegistrationRepository
  private lateinit var viewModel: RegistrationViewModel
  private var backDispatcher: OnBackPressedDispatcher? = null

  private val backupFolderUri: Uri = Uri.parse("content://test/backups")

  @Before
  fun setup() {
    Log.initialize(SystemOutLogger())

    val context = ApplicationProvider.getApplicationContext<Application>()
    Shadows.shadowOf(context).grantPermissions(*RegistrationPermissions.getRequiredPermissions(context).toTypedArray())

    networkController = FakeNetworkController()
    storageController = FakeStorageController()
    purchaseApi = FakeOneTimePurchaseApi()
    repository = RegistrationRepository(context, networkController, storageController, isLinkAndSyncAvailable = false, signalLoginPurchaseApi = OneTimePurchaseApi.Empty)
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `happy path - new registration by entering phone number, verification code, and creating a pin`() {
    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)
    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(committed.accountData?.aci?.isNotEmpty() == true) { "Expected committed ACI to be populated" }
    assert(committed.accountData?.pni?.isNotEmpty() == true) { "Expected committed PNI to be populated" }
    assert(committed.pin == PIN) { "Expected committed pin $PIN but was ${committed.pin}" }
    assert(committed.accountEntropyPool.isNotEmpty()) { "Expected committed AEP to be populated" }
    assert(committed.accountData?.reRegistration == false) { "Expected a new registration to not be flagged as a re-registration" }
    assert(storageController.registrationFlowFinishedCount == 1) { "Expected the flow-finished hook to fire exactly once but fired ${storageController.registrationFlowFinishedCount} times" }

    assert(networkController.lastCreateSessionE164 == E164) { "Expected a session for $E164 but was ${networkController.lastCreateSessionE164}" }
    assert(networkController.lastRegisterAccountRequest?.e164 == E164) { "Expected registration for $E164 but was ${networkController.lastRegisterAccountRequest}" }
    assert(networkController.lastSetPinRequest?.pin == PIN) { "Expected pin $PIN on SVR but was ${networkController.lastSetPinRequest?.pin}" }
    assert(networkController.accountAttributesSyncJobEnqueued) { "Expected the account attributes sync job to be enqueued" }

    assert(storageController.restoreDecision == RestoreDecision.NEW_ACCOUNT) { "Expected NEW_ACCOUNT restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `entering the verification code as a new pin warns the user and blocks it until a different pin is chosen`() {
    val warning = ApplicationProvider.getApplicationContext<Application>().getString(R.string.PinCreationScreen__reentered_verification_code)

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)

    // On the PIN creation screen, re-entering the verification code as the new PIN is rejected with a warning
    waitForTag(TestTags.PIN_CREATION_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_INPUT).performTextInput(VERIFICATION_CODE)
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_NEXT_BUTTON).performClick()

    waitForText(warning)
    assert(composeTestRule.onAllNodesWithTag(TestTags.PIN_CREATION_CONFIRM_INPUT).fetchSemanticsNodes().isEmpty()) {
      "Expected to stay on the PIN creation step rather than advancing to confirmation"
    }
    assert(networkController.lastSetPinRequest == null) { "Should not have backed up the verification code as a PIN" }

    // Choosing a different PIN is accepted and completes registration
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_INPUT).performTextClearance()
    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.pin == PIN) { "Expected committed pin $PIN but was ${committed.pin}" }
    assert(networkController.lastSetPinRequest?.pin == PIN) { "Expected pin $PIN on SVR but was ${networkController.lastSetPinRequest?.pin}" }
  }

  @Test
  fun `entering the verification code as an existing pin warns the user before the correct pin restores the account`() {
    val warning = ApplicationProvider.getApplicationContext<Application>().getString(R.string.PinEntryScreen__reentered_verification_code)
    val masterKey = MasterKey(ByteArray(32) { it.toByte() })

    // The account already has SVR data, so after verification the user is asked to enter their existing PIN
    networkController.onRegisterAccount = { request ->
      RequestResult.Success(networkController.registerAccountResponse(request.e164, storageCapable = true))
    }
    networkController.onGetSvrCredentials = {
      RequestResult.Success(SvrCredentials(username = "svr-user", password = "svr-pass"))
    }
    networkController.onRestoreMasterKeyFromSvr = { request ->
      if (request.pin == PIN) {
        RequestResult.Success(MasterKeyResponse(masterKey))
      } else {
        RequestResult.NonSuccess(RestoreMasterKeyError.WrongPin(triesRemaining = 3))
      }
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)

    // On the PIN entry screen, entering the verification code as the PIN is a wrong PIN and warns the user
    waitForTag(TestTags.PIN_ENTRY_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_INPUT).performTextInput(VERIFICATION_CODE)
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_CONTINUE_BUTTON).performClick()

    waitForText(warning)
    assert(!registrationComplete) { "Registration should not complete with the verification code as the PIN" }

    // Entering the correct PIN restores the account and completes registration
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_INPUT).performTextClearance()
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_INPUT).performTextInput(PIN)
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_CONTINUE_BUTTON).performClick()

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRestoreMasterKeyRequest?.pin == PIN) { "Expected master key restore with pin $PIN but was ${networkController.lastRestoreMasterKeyRequest}" }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(storageController.restoreDecision == RestoreDecision.COMPLETED) { "Expected COMPLETED restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `a captcha submission for a session that no longer exists resets the flow, which can then be restarted to completion`() {
    // The session demands a captcha, but expires server-side before the solved captcha is submitted
    networkController.onCreateSession = {
      RequestResult.Success(networkController.session(allowedToRequestCode = false, requestedInformation = listOf("captcha")))
    }
    networkController.onUpdateSession = {
      RequestResult.NonSuccess(UpdateSessionError.SessionNotFound("no session found"))
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    submitPhoneNumber()
    solveCaptcha("captcha-token")

    // The server no longer knows the session, so the flow resets back to the beginning
    waitForTag(TestTags.WELCOME_SCREEN)
    assert(networkController.lastUpdateSessionRequest?.captchaToken == "captcha-token") {
      "Expected the solved captcha to be submitted but was ${networkController.lastUpdateSessionRequest}"
    }
    assert(storageController.committedData == null) { "Expected no registration data to be committed" }

    // Starting over against a healthy server completes registration
    networkController.onCreateSession = { RequestResult.Success(networkController.session()) }
    networkController.onUpdateSession = { RequestResult.Success(networkController.session()) }

    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)
    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
  }

  @Test
  fun `a registration lock is unlocked by entering the existing pin and registration completes`() {
    val masterKey = MasterKey(ByteArray(32) { it.toByte() })

    networkController.onRegisterAccount = { request ->
      if (request.registrationLock == null) {
        RequestResult.NonSuccess(
          RegisterAccountError.RegistrationLock(
            RegistrationLockResponse(
              timeRemaining = 14.days.inWholeMilliseconds,
              svr2Credentials = SvrCredentials(username = "svr-user", password = "svr-pass")
            )
          )
        )
      } else {
        RequestResult.Success(networkController.registerAccountResponse(request.e164))
      }
    }

    networkController.onRestoreMasterKeyFromSvr = { request ->
      if (request.pin == PIN) {
        RequestResult.Success(MasterKeyResponse(masterKey))
      } else {
        RequestResult.NonSuccess(RestoreMasterKeyError.WrongPin(triesRemaining = 3))
      }
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)

    // The account is reglocked, so the user must prove they know their existing PIN
    waitForTag(TestTags.PIN_ENTRY_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_INPUT).performTextInput(PIN)
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_CONTINUE_BUTTON).performClick()

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRestoreMasterKeyRequest?.pin == PIN) { "Expected master key restore with pin $PIN but was ${networkController.lastRestoreMasterKeyRequest}" }
    assert(networkController.lastRegisterAccountRequest?.registrationLock == masterKey.deriveRegistrationLock()) { "Expected registration with the derived reglock token" }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(committed.accountData?.aci?.isNotEmpty() == true) { "Expected committed ACI to be populated" }
  }

  @Test
  fun `a rejected sms-bypass recovery password falls back to sms verification, and the reglock pin refetches the master key`() {
    val masterKey = MasterKey(ByteArray(32) { 0x11 })
    val storedCredentials = SvrCredentials(username = "stored-svr-user", password = "stored-svr-pass")
    val reglockCredentials = SvrCredentials(username = "reglock-svr-user", password = "reglock-svr-pass")

    // A previous registration attempt left SVR credentials behind, enabling the SMS-bypass path
    runBlocking {
      storageController.updateInProgressRegistrationData {
        svrCredentials += SvrCredential(username = storedCredentials.username, password = storedCredentials.password)
      }
    }
    networkController.onCheckSvrCredentials = { _, credentials ->
      val credential = credentials.first()
      RequestResult.Success(CheckSvrCredentialsResponse(matches = mapOf("${credential.username}:${credential.password}" to "match")))
    }

    // SVR consistently returns the same master key for the correct PIN
    val restoreRequests = mutableListOf<FakeNetworkController.RestoreMasterKeyRequest>()
    networkController.onRestoreMasterKeyFromSvr = { request ->
      restoreRequests += request
      if (request.pin == PIN) {
        RequestResult.Success(MasterKeyResponse(masterKey))
      } else {
        RequestResult.NonSuccess(RestoreMasterKeyError.WrongPin(triesRemaining = 3))
      }
    }

    // The server's state is borked such that the RRP derived from the master key is rejected, even though the same
    // master key still governs the reglock. Only a verified session with the reglock token can register.
    val registerRequests = mutableListOf<FakeNetworkController.RegisterAccountRequest>()
    networkController.onRegisterAccount = { request ->
      registerRequests += request
      when {
        request.recoveryPassword != null -> RequestResult.NonSuccess(RegisterAccountError.RegistrationRecoveryPasswordIncorrect("wrong recovery password"))
        request.registrationLock == masterKey.deriveRegistrationLock() -> RequestResult.Success(networkController.registerAccountResponse(request.e164))
        else -> RequestResult.NonSuccess(
          RegisterAccountError.RegistrationLock(
            RegistrationLockResponse(
              timeRemaining = 14.days.inWholeMilliseconds,
              svr2Credentials = reglockCredentials
            )
          )
        )
      }
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    submitPhoneNumber()

    // The stored SVR credentials are valid, so the user is offered to enter their PIN to skip SMS verification
    waitForTag(TestTags.PIN_ENTRY_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_INPUT).performTextInput(PIN)
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_CONTINUE_BUTTON).performClick()

    // The recovery password derived from the restored master key is rejected, landing back on phone number entry.
    // Resubmitting the number now goes through SMS verification instead of another recovery password attempt.
    resubmitPhoneNumber()

    submitVerificationCode(VERIFICATION_CODE)

    // The account is reglocked, so the user must prove they know their existing PIN
    waitForTag(TestTags.PIN_ENTRY_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_INPUT).performTextInput(PIN)
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_CONTINUE_BUTTON).performClick()

    waitFor("registration to complete") { registrationComplete }

    // The reglock master key was refetched with the entered PIN against the challenge's credentials, not reused from the earlier restore
    assert(restoreRequests.size == 2) { "Expected two master key restores (sms-bypass, then reglock) but was $restoreRequests" }
    assert(restoreRequests.last() == FakeNetworkController.RestoreMasterKeyRequest(reglockCredentials, PIN)) {
      "Expected the reglock master key restore to use the challenge credentials and the entered PIN but was ${restoreRequests.last()}"
    }

    assert(registerRequests.count { it.recoveryPassword != null } == 1) {
      "Expected the recovery password to never be retried after being rejected, but the requests were $registerRequests"
    }

    val finalRequest = networkController.lastRegisterAccountRequest
    assert(finalRequest?.sessionId != null) { "Expected the final registration to use the verified session but was $finalRequest" }
    assert(finalRequest?.recoveryPassword == null) { "Expected the final registration to carry no recovery password but was $finalRequest" }
    assert(finalRequest?.registrationLock == masterKey.deriveRegistrationLock()) { "Expected the reglock token to be derived from the restored master key but was $finalRequest" }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(committed.pin == PIN) { "Expected committed pin $PIN but was ${committed.pin}" }
  }

  @Test
  fun `restoring a remote backup for a reglocked account bypasses the reglock with a proof derived from the entered aep`() {
    val aep = AccountEntropyPool.generate()
    val reglockProof = aep.deriveMasterKey().deriveRegistrationLock()

    networkController.onRegisterAccount = { request ->
      if (request.registrationLock == reglockProof) {
        RequestResult.Success(networkController.registerAccountResponse(request.e164))
      } else {
        RequestResult.NonSuccess(
          RegisterAccountError.RegistrationLock(
            RegistrationLockResponse(
              timeRemaining = 14.days.inWholeMilliseconds,
              svr2Credentials = SvrCredentials(username = "svr-user", password = "svr-pass")
            )
          )
        )
      }
    }

    // The backup contains the user's PIN, so no PIN screens are needed after the restore
    storageController.onRestoreRemoteBackup = {
      flowOf(RemoteBackupRestoreProgress.Complete(restoredSvrPin = PIN, restoredProfileKey = null))
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startManualRestore()
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS)
    enterPhoneNumber()
    enterAep(aep)

    // The reglock is bypassed automatically with the proof derived from the AEP, going straight to the restore
    startRemoteRestore()

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRegisterAccountRequest?.registrationLock == reglockProof) { "Expected registration with the reglock proof derived from the entered AEP" }
    assert(networkController.lastRestoreMasterKeyRequest == null) { "Should not have needed to restore the master key from SVR" }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(committed.accountEntropyPool == aep.value) { "Expected the committed AEP to be the one the user entered" }
    assert(committed.pin == PIN) { "Expected the pin from the restored backup but was ${committed.pin}" }
    assert(storageController.restoreDecision == RestoreDecision.COMPLETED) { "Expected COMPLETED restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `restoring a remote backup for a reglocked account whose reglock is not derived from the aep falls back to pin entry then resumes the restore`() {
    val aep = AccountEntropyPool.generate()
    val svrMasterKey = MasterKey(ByteArray(32) { it.toByte() })

    // The account's reglock is governed by a master key that is not derived from the AEP, so the derived proof fails
    networkController.onRegisterAccount = { request ->
      if (request.registrationLock == svrMasterKey.deriveRegistrationLock()) {
        RequestResult.Success(networkController.registerAccountResponse(request.e164))
      } else {
        RequestResult.NonSuccess(
          RegisterAccountError.RegistrationLock(
            RegistrationLockResponse(
              timeRemaining = 14.days.inWholeMilliseconds,
              svr2Credentials = SvrCredentials(username = "svr-user", password = "svr-pass")
            )
          )
        )
      }
    }

    networkController.onRestoreMasterKeyFromSvr = { request ->
      if (request.pin == PIN) {
        RequestResult.Success(MasterKeyResponse(svrMasterKey))
      } else {
        RequestResult.NonSuccess(RestoreMasterKeyError.WrongPin(triesRemaining = 3))
      }
    }

    // The backup contains the user's PIN, so no PIN screens are needed after the restore
    storageController.onRestoreRemoteBackup = {
      flowOf(RemoteBackupRestoreProgress.Complete(restoredSvrPin = PIN, restoredProfileKey = null))
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startManualRestore()
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS)
    enterPhoneNumber()
    enterAep(aep)

    // The derived proof was rejected, so the user must prove they know their existing PIN
    waitForTag(TestTags.PIN_ENTRY_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_INPUT).performTextInput(PIN)
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_CONTINUE_BUTTON).performClick()

    // With the reglock cleared, the remote restore the user chose resumes rather than being dropped
    startRemoteRestore()

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRestoreMasterKeyRequest?.pin == PIN) { "Expected master key restore with pin $PIN but was ${networkController.lastRestoreMasterKeyRequest}" }
    assert(networkController.lastRegisterAccountRequest?.sessionId == null) { "Expected registration without a session but was ${networkController.lastRegisterAccountRequest}" }
    assert(networkController.lastRegisterAccountRequest?.recoveryPassword == svrMasterKey.deriveRegistrationRecoveryPassword()) { "Expected registration via the RRP derived from the restored master key" }
    assert(networkController.lastRegisterAccountRequest?.registrationLock == svrMasterKey.deriveRegistrationLock()) { "Expected registration with the reglock proof derived from the restored master key" }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(storageController.restoreDecision == RestoreDecision.COMPLETED) { "Expected COMPLETED restore decision (the remote backup was restored) but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `restoring a local backup for a reglocked account bypasses the reglock with a proof derived from the restored aep`() {
    val aep = AccountEntropyPool.generate()
    val reglockProof = aep.deriveMasterKey().deriveRegistrationLock()

    networkController.onRegisterAccount = { request ->
      if (request.registrationLock == reglockProof) {
        RequestResult.Success(networkController.registerAccountResponse(request.e164))
      } else {
        RequestResult.NonSuccess(
          RegisterAccountError.RegistrationLock(
            RegistrationLockResponse(
              timeRemaining = 14.days.inWholeMilliseconds,
              svr2Credentials = SvrCredentials(username = "svr-user", password = "svr-pass")
            )
          )
        )
      }
    }

    var registrationComplete = false
    launchRegistrationFlow(folderPickerResult = backupFolderUri, onRegistrationComplete = { registrationComplete = true })

    startManualRestore()
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FOLDER)
    enterPhoneNumber()
    restoreLocalBackup(aep)

    // The reglock is bypassed automatically with the proof derived from the restored AEP, and the user creates a PIN
    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRegisterAccountRequest?.registrationLock == reglockProof) { "Expected registration with the reglock proof derived from the restored AEP" }
    assert(networkController.lastRestoreMasterKeyRequest == null) { "Should not have needed to restore the master key from SVR" }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(committed.accountEntropyPool == aep.value) { "Expected the committed AEP to be the one from the restored backup" }
    assert(committed.pin == PIN) { "Expected committed pin $PIN but was ${committed.pin}" }
  }

  @Test
  fun `quick restore for a reglocked account bypasses the reglock with a proof derived from the provisioned aep`() {
    val aep = AccountEntropyPool.generate()
    val reglockProof = aep.deriveMasterKey().deriveRegistrationLock()

    networkController.onStartProvisioning = {
      flowOf(
        ProvisioningEvent.QrCodeReady("https://signal.test/qr"),
        ProvisioningEvent.MessageReceived(networkController.provisioningMessage(aep = aep, e164 = E164, pin = PIN))
      )
    }

    networkController.onRegisterAccount = { request ->
      if (request.registrationLock == reglockProof) {
        RequestResult.Success(networkController.registerAccountResponse(request.e164))
      } else {
        RequestResult.NonSuccess(
          RegisterAccountError.RegistrationLock(
            RegistrationLockResponse(
              timeRemaining = 14.days.inWholeMilliseconds,
              svr2Credentials = SvrCredentials(username = "svr-user", password = "svr-pass")
            )
          )
        )
      }
    }

    // The backup contains the user's PIN, so no PIN screens are needed after the restore
    storageController.onRestoreRemoteBackup = {
      flowOf(RemoteBackupRestoreProgress.Complete(restoredSvrPin = PIN, restoredProfileKey = null))
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    // Registration happens automatically with the provisioned data, bypassing the reglock, landing on restore selection
    startQuickRestore()
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS)
    startRemoteRestore()

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRegisterAccountRequest?.registrationLock == reglockProof) { "Expected registration with the reglock proof derived from the provisioned AEP" }
    assert(networkController.lastRestoreMasterKeyRequest == null) { "Should not have needed to restore the master key from SVR" }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(committed.accountEntropyPool == aep.value) { "Expected the committed AEP to be the provisioned one" }
    assert(committed.pin == PIN) { "Expected the pin from the restored backup but was ${committed.pin}" }
  }

  @Test
  fun `re-registering an existing account offers restore selection, which can be skipped to complete registration`() {
    networkController.onRegisterAccount = { request ->
      RequestResult.Success(networkController.registerAccountResponse(request.e164, reregistration = true))
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)

    // The user is re-registering, so they're offered a restore. Decline it, confirming the skip warning.
    waitForTag(TestTags.ARCHIVE_RESTORE_SELECTION_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.ARCHIVE_RESTORE_SELECTION_NONE).performClick()
    waitForTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(committed.pin == PIN) { "Expected committed pin $PIN but was ${committed.pin}" }
    assert(committed.accountData?.reRegistration == true) { "Expected the committed account data to be flagged as a re-registration" }

    // The re-registration flag is what tells the app to reclaim the username we just released, and the flow-finished
    // hook is where it enqueues the job that does it. See AppRegistrationStorageController.
    assert(storageController.registrationFlowFinishedCount == 1) { "Expected the flow-finished hook to fire exactly once but fired ${storageController.registrationFlowFinishedCount} times" }
  }

  @Test
  fun `re-registering the same number offers no restore on the welcome screen and completes without a restore prompt`() {
    storageController.preExistingRegistrationData = preExistingRegistrationData(E164)

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    // The welcome screen does not offer restore or transfer during a re-registration
    waitForTag(TestTags.WELCOME_SCREEN)
    assert(composeTestRule.onAllNodesWithTag(TestTags.WELCOME_RESTORE_OR_TRANSFER_BUTTON).fetchSemanticsNodes().isEmpty()) {
      "Expected no restore/transfer option on the welcome screen during re-registration"
    }

    // Continue to phone entry, where the previous number is prefilled, and confirm it
    composeTestRule.onNodeWithTag(TestTags.WELCOME_GET_STARTED_BUTTON).performClick()
    waitForTag(TestTags.PHONE_NUMBER_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_NEXT_BUTTON).performClick()
    waitForTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    // The account re-registers via the recovery password and goes straight to PIN creation, never offering a restore
    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
  }

  @Test
  fun `re-registering the same number onto an unexpectedly reglocked account is unlocked by entering the pin, without an sms verification`() {
    // The reglock is governed by a master key held in SVR, not the one derived from the pre-existing AEP
    val svrMasterKey = MasterKey(ByteArray(32) { it.toByte() })

    // The device's own data says reglock is off, so the fast path registers without a reglock token and is rejected
    storageController.preExistingRegistrationData = preExistingRegistrationData(E164)

    networkController.onRegisterAccount = { request ->
      if (request.registrationLock == svrMasterKey.deriveRegistrationLock()) {
        RequestResult.Success(networkController.registerAccountResponse(request.e164, reregistration = true))
      } else {
        RequestResult.NonSuccess(
          RegisterAccountError.RegistrationLock(
            RegistrationLockResponse(
              timeRemaining = 14.days.inWholeMilliseconds,
              svr2Credentials = SvrCredentials(username = "svr-user", password = "svr-pass")
            )
          )
        )
      }
    }

    networkController.onRestoreMasterKeyFromSvr = { request ->
      if (request.pin == PIN) {
        RequestResult.Success(MasterKeyResponse(svrMasterKey))
      } else {
        RequestResult.NonSuccess(RestoreMasterKeyError.WrongPin(triesRemaining = 3))
      }
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    // Continue to phone entry, where the previous number is prefilled, and confirm it
    waitForTag(TestTags.WELCOME_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.WELCOME_GET_STARTED_BUTTON).performClick()
    waitForTag(TestTags.PHONE_NUMBER_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_NEXT_BUTTON).performClick()
    waitForTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    // The recovery-password fast path hit an unexpected reglock, so the user must prove they know their existing PIN
    waitForTag(TestTags.PIN_ENTRY_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_INPUT).performTextInput(PIN)
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_CONTINUE_BUTTON).performClick()

    // The e164 chosen on the fast path is still known, so the PIN screen can register rather than resetting the flow
    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRestoreMasterKeyRequest?.pin == PIN) { "Expected master key restore with pin $PIN but was ${networkController.lastRestoreMasterKeyRequest}" }

    val finalRequest = networkController.lastRegisterAccountRequest
    assert(finalRequest?.e164 == E164) { "Expected registration for $E164 but was $finalRequest" }
    assert(finalRequest?.sessionId == null) { "Expected registration without a session, since the fast path never created one, but was $finalRequest" }
    assert(finalRequest?.recoveryPassword == svrMasterKey.deriveRegistrationRecoveryPassword()) { "Expected registration via the RRP derived from the restored master key but was $finalRequest" }
    assert(finalRequest?.registrationLock == svrMasterKey.deriveRegistrationLock()) { "Expected registration with the reglock token derived from the restored master key but was $finalRequest" }

    assert(networkController.lastCreateSessionE164 == null) { "Expected no SMS verification session to be created but was ${networkController.lastCreateSessionE164}" }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(committed.accountData?.aci?.isNotEmpty() == true) { "Expected committed ACI to be populated" }
  }

  @Test
  fun `re-registering with pre-existing data through sms verification skips the post-registration restore prompt`() {
    // Pre-existing data for a different number, so the same-number recovery-password fast path is skipped and the flow
    // goes through SMS verification, exercising the post-registration re-registration branch.
    storageController.preExistingRegistrationData = preExistingRegistrationData("+15557654321")

    networkController.onRegisterAccount = { request ->
      RequestResult.Success(networkController.registerAccountResponse(request.e164, reregistration = true))
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    waitForTag(TestTags.WELCOME_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.WELCOME_GET_STARTED_BUTTON).performClick()

    // The previous number is prefilled; replace it with a different number and confirm
    waitForTag(TestTags.PHONE_NUMBER_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_PHONE_FIELD).performTextClearance()
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_PHONE_FIELD).performTextInput(PHONE_NUMBER)
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_NEXT_BUTTON).performClick()
    waitForTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    submitVerificationCode(VERIFICATION_CODE)

    // The server reports a re-registration, but the pre-existing data suppresses the restore prompt and the user
    // proceeds straight to PIN creation.
    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(committed.pin == PIN) { "Expected committed pin $PIN but was ${committed.pin}" }
  }

  @Test
  fun `opting out of creating a pin still completes registration`() {
    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)

    // Instead of creating a PIN, disable PINs via the overflow menu, confirming the warning
    waitForTag(TestTags.PIN_CREATION_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_MENU_BUTTON).performClick()
    waitForTag(TestTags.PIN_CREATION_DISABLE_PIN_MENU_ITEM)
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_DISABLE_PIN_MENU_ITEM).performClick()
    waitForTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.pinOptedOut) { "Expected the committed data to record the PIN opt-out" }
    assert(committed.pin.isEmpty()) { "Expected no committed pin but was ${committed.pin}" }
    assert(networkController.lastSetPinRequest == null) { "Should not have backed up a pin to SVR" }
    assert(storageController.restoreDecision == RestoreDecision.NEW_ACCOUNT) { "Expected NEW_ACCOUNT restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `restoring a remote backup before registering completes registration`() {
    val aep = AccountEntropyPool.generate()

    // The backup contains the user's PIN, so no PIN screens are needed after the restore
    storageController.onRestoreRemoteBackup = {
      flowOf(RemoteBackupRestoreProgress.Complete(restoredSvrPin = PIN, restoredProfileKey = null))
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startManualRestore()
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS)
    enterPhoneNumber()
    enterAep(aep)
    startRemoteRestore()

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRegisterAccountRequest?.recoveryPassword == aep.deriveMasterKey().deriveRegistrationRecoveryPassword()) {
      "Expected registration via the recovery password derived from the entered AEP"
    }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(committed.accountEntropyPool == aep.value) { "Expected the committed AEP to be the one the user entered" }
    assert(committed.pin == PIN) { "Expected the pin from the restored backup but was ${committed.pin}" }
    assert(storageController.restoreDecision == RestoreDecision.COMPLETED) { "Expected COMPLETED restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `a remote restore whose auth credential fails verification re-commits the backup-id and carries on`() {
    val aep = AccountEntropyPool.generate()

    // The service is still issuing credentials against a backup-id from before the account was last re-registered, so
    // nothing it hands out can verify until the client re-commits the one derived from the AEP in hand.
    networkController.onGetRemoteBackupInfo = {
      if (networkController.reserveBackupIdCount == 0) {
        RequestResult.NonSuccess(NetworkController.GetBackupInfoError.CredentialVerificationFailed)
      } else {
        RequestResult.Success(NetworkController.GetBackupInfoResponse(cdn = 3, backupDir = "backup-dir", mediaDir = "media-dir", backupName = "backup", usedSpace = 1_000_000))
      }
    }

    // The backup contains the user's PIN, so no PIN screens are needed after the restore
    storageController.onRestoreRemoteBackup = {
      flowOf(RemoteBackupRestoreProgress.Complete(restoredSvrPin = PIN, restoredProfileKey = null))
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startManualRestore()
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS)
    enterPhoneNumber()
    enterAep(aep)

    // The restore screen only offers the restore once the backup info has loaded, which it can't do until the backup-id
    // has been re-committed underneath it
    startRemoteRestore()

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.reserveBackupIdCount == 1) { "Expected the backup-id to be re-committed exactly once but was ${networkController.reserveBackupIdCount}" }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountEntropyPool == aep.value) { "Expected the committed AEP to be the one the user entered" }
    assert(committed.pin == PIN) { "Expected the pin from the restored backup but was ${committed.pin}" }
    assert(storageController.restoreDecision == RestoreDecision.COMPLETED) { "Expected COMPLETED restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `a remote restore whose backup-id cannot be re-committed surfaces a load failure`() {
    val aep = AccountEntropyPool.generate()

    networkController.onGetRemoteBackupInfo = { RequestResult.NonSuccess(NetworkController.GetBackupInfoError.CredentialVerificationFailed) }
    networkController.onReserveBackupId = { RequestResult.NonSuccess(NetworkController.ReserveBackupIdError.Unauthorized) }

    launchRegistrationFlow()

    startManualRestore()
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS)
    enterPhoneNumber()
    enterAep(aep)

    waitForText(ApplicationProvider.getApplicationContext<Application>().getString(R.string.RemoteRestoreScreen__cant_restore_backup))

    assert(networkController.reserveBackupIdCount == 1) { "Expected a single attempt to re-commit the backup-id but was ${networkController.reserveBackupIdCount}" }
  }

  @Test
  fun `restoring a local backup before registering completes registration`() {
    val aep = AccountEntropyPool.generate()

    var registrationComplete = false
    launchRegistrationFlow(folderPickerResult = backupFolderUri, onRegistrationComplete = { registrationComplete = true })

    startManualRestore()
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FOLDER)
    enterPhoneNumber()
    restoreLocalBackup(aep)

    // With the backup restored, registration happens via the recovery password and the user creates a PIN
    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRegisterAccountRequest?.recoveryPassword == aep.deriveMasterKey().deriveRegistrationRecoveryPassword()) {
      "Expected registration via the recovery password derived from the restored AEP"
    }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(committed.accountEntropyPool == aep.value) { "Expected the committed AEP to be the one from the restored backup" }
    assert(committed.pin == PIN) { "Expected committed pin $PIN but was ${committed.pin}" }
  }

  @Test
  fun `restoring a remote backup after registering completes registration`() {
    val aep = AccountEntropyPool.generate()

    networkController.onRegisterAccount = { request ->
      RequestResult.Success(networkController.registerAccountResponse(request.e164, reregistration = true))
    }

    // The backup contains the user's PIN, so no PIN screens are needed after the restore
    storageController.onRestoreRemoteBackup = {
      flowOf(RemoteBackupRestoreProgress.Complete(restoredSvrPin = PIN, restoredProfileKey = null))
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)

    // The user is re-registering, so they're offered a restore
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS)
    enterAep(aep)
    startRemoteRestore()

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountEntropyPool == aep.value) { "Expected the committed AEP to be the one the user entered" }
    assert(committed.pin == PIN) { "Expected the pin from the restored backup but was ${committed.pin}" }
    assert(storageController.restoreDecision == RestoreDecision.COMPLETED) { "Expected COMPLETED restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `restoring a remote backup without a pin after registering requires creating a pin`() {
    val aep = AccountEntropyPool.generate()

    networkController.onRegisterAccount = { request ->
      RequestResult.Success(networkController.registerAccountResponse(request.e164, reregistration = true))
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)

    // The user is re-registering, so they're offered a restore
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS)
    enterAep(aep)
    startRemoteRestore()

    // The restored backup had no PIN and the account is not storage capable, so the user must create a PIN
    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(committed.pin == PIN) { "Expected committed pin $PIN but was ${committed.pin}" }
    assert(networkController.lastSetPinRequest?.pin == PIN) { "Expected pin $PIN on SVR but was ${networkController.lastSetPinRequest?.pin}" }
  }

  @Test
  fun `restoring a local backup after registering completes registration`() {
    val aep = AccountEntropyPool.generate()

    networkController.onRegisterAccount = { request ->
      RequestResult.Success(networkController.registerAccountResponse(request.e164, reregistration = true))
    }

    // The backup contains the user's PIN, so the flow finishes without any PIN screens
    storageController.onRestoreLocalBackupV2 = { _, _ ->
      flowOf(LocalBackupRestoreProgress.Complete(restoredSvrPin = PIN, restoredProfileKey = null))
    }

    var registrationComplete = false
    launchRegistrationFlow(folderPickerResult = backupFolderUri, onRegistrationComplete = { registrationComplete = true })

    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)

    // The user is re-registering, so they're offered a restore
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FOLDER)
    restoreLocalBackup(aep)

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.pin == PIN) { "Expected the pin from the restored backup but was ${committed.pin}" }
    assert(storageController.restoreDecision == RestoreDecision.COMPLETED) { "Expected COMPLETED restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `restoring a local backup without a pin after registering requires creating a pin`() {
    val aep = AccountEntropyPool.generate()

    networkController.onRegisterAccount = { request ->
      RequestResult.Success(networkController.registerAccountResponse(request.e164, reregistration = true))
    }

    var registrationComplete = false
    launchRegistrationFlow(folderPickerResult = backupFolderUri, onRegistrationComplete = { registrationComplete = true })

    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)

    // The user is re-registering, so they're offered a restore
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FOLDER)
    restoreLocalBackup(aep)

    // The backup had no PIN and the account is not storage capable, so the user must create a PIN
    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(committed.pin == PIN) { "Expected committed pin $PIN but was ${committed.pin}" }
    assert(networkController.lastSetPinRequest?.pin == PIN) { "Expected pin $PIN on SVR but was ${networkController.lastSetPinRequest?.pin}" }
  }

  @Test
  fun `entering an incorrect aep for a remote restore shows an error and allows retrying`() {
    val correctAep = AccountEntropyPool.generate()
    val wrongAep = AccountEntropyPool.generate()
    val correctRecoveryPassword = correctAep.deriveMasterKey().deriveRegistrationRecoveryPassword()

    networkController.onRegisterAccount = { request ->
      if (request.recoveryPassword == correctRecoveryPassword) {
        RequestResult.Success(networkController.registerAccountResponse(request.e164))
      } else {
        RequestResult.NonSuccess(RegisterAccountError.RegistrationRecoveryPasswordIncorrect("wrong recovery password"))
      }
    }

    // The backup contains the user's PIN, so no PIN screens are needed after the restore
    storageController.onRestoreRemoteBackup = {
      flowOf(RemoteBackupRestoreProgress.Complete(restoredSvrPin = PIN, restoredProfileKey = null))
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startManualRestore()
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS)
    enterPhoneNumber()
    enterAep(wrongAep)

    // The server rejects the recovery password derived from the wrong AEP, disabling submission until the key changes.
    // Wait on the error text rather than the disabled button, which is also disabled while the attempt is in flight.
    waitForText(ApplicationProvider.getApplicationContext<Application>().getString(R.string.EnterAepScreen__incorrect_recovery_key))
    assert(
      composeTestRule.onAllNodesWithTag(TestTags.ENTER_AEP_NEXT_BUTTON).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.Disabled) != null
    ) {
      "Expected submission to be disabled after the AEP was rejected"
    }
    assert(networkController.lastRegisterAccountRequest?.recoveryPassword == wrongAep.deriveMasterKey().deriveRegistrationRecoveryPassword()) {
      "Expected a registration attempt with the wrong recovery password but was ${networkController.lastRegisterAccountRequest}"
    }

    // Entering the correct AEP clears the error and the restore proceeds
    composeTestRule.onNodeWithTag(TestTags.ENTER_AEP_INPUT).performTextClearance()
    enterAep(correctAep)
    startRemoteRestore()

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountEntropyPool == correctAep.value) { "Expected the committed AEP to be the correct one" }
    assert(storageController.restoreDecision == RestoreDecision.COMPLETED) { "Expected COMPLETED restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `restoring a local backup for a different account warns the user, verifies over sms, then imports the backup`() {
    val aep = AccountEntropyPool.generate()

    // The AEP decrypts the backup fine, but it doesn't belong to the account for this phone number
    val registerRequests = mutableListOf<FakeNetworkController.RegisterAccountRequest>()
    networkController.onRegisterAccount = { request ->
      registerRequests += request
      if (request.recoveryPassword != null) {
        RequestResult.NonSuccess(RegisterAccountError.RegistrationRecoveryPasswordIncorrect("wrong recovery password"))
      } else {
        RequestResult.Success(networkController.registerAccountResponse(request.e164))
      }
    }

    // The import only ever runs against a registered account, so it must come after SMS verification
    var registerAttemptsWhenRestoreRan = -1
    val defaultLocalRestore = storageController.onRestoreLocalBackupV2
    storageController.onRestoreLocalBackupV2 = { backupUri, restoreAep ->
      registerAttemptsWhenRestoreRan = registerRequests.size
      defaultLocalRestore(backupUri, restoreAep)
    }

    var registrationComplete = false
    launchRegistrationFlow(folderPickerResult = backupFolderUri, onRegistrationComplete = { registrationComplete = true })

    startManualRestore()
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FOLDER)
    enterPhoneNumber()
    restoreLocalBackup(aep)

    // The key decrypts the backup but the recovery password is rejected, meaning the backup belongs to a different
    // account. The user is warned and confirms restoring it to this account anyway.
    waitForTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    // Confirming requires verifying the phone number over SMS. Afterwards the restore resumes on the standard
    // post-registration local restore screen, where the folder is re-selected (the entered key is reused).
    submitVerificationCode(VERIFICATION_CODE)
    restoreFoundLocalBackup()
    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    assert(registerRequests.first().recoveryPassword == aep.deriveMasterKey().deriveRegistrationRecoveryPassword()) {
      "Expected the first registration attempt to use the recovery password derived from the backup's AEP"
    }
    assert(networkController.lastRegisterAccountRequest?.sessionId != null) { "Expected the final registration to use a verified session" }
    assert(registerAttemptsWhenRestoreRan == registerRequests.size) {
      "Expected the backup to be imported only after registration completed, but the import ran after $registerAttemptsWhenRestoreRan of ${registerRequests.size} registration attempts"
    }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(committed.pin == PIN) { "Expected committed pin $PIN but was ${committed.pin}" }
    assert(committed.accountEntropyPool == aep.value) {
      "Expected the entered recovery key to be adopted as the committed AEP"
    }
    assert(networkController.lastSetPinRequest?.masterKey == aep.deriveMasterKey()) {
      "Expected the new PIN to be backed up to SVR with the master key derived from the adopted AEP"
    }
    assert(storageController.restoreDecision == RestoreDecision.COMPLETED) { "Expected COMPLETED restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `declining to restore a local backup for a different account returns to the recovery key entry screen`() {
    val aep = AccountEntropyPool.generate()

    networkController.onRegisterAccount = { request ->
      if (request.recoveryPassword != null) {
        RequestResult.NonSuccess(RegisterAccountError.RegistrationRecoveryPasswordIncorrect("wrong recovery password"))
      } else {
        RequestResult.Success(networkController.registerAccountResponse(request.e164))
      }
    }

    launchRegistrationFlow(folderPickerResult = backupFolderUri)

    startManualRestore()
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FOLDER)
    enterPhoneNumber()
    restoreLocalBackup(aep)

    // The user is warned that the backup belongs to a different account and declines
    waitForTag(Dialogs.TEST_TAG_ALERT_DIALOG_DISMISS_BUTTON)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_DISMISS_BUTTON).performClick()

    // The user stays on the recovery key entry screen and nothing was restored or registered
    waitForTag(TestTags.ENTER_AEP_SCREEN)
    assert(storageController.committedData == null) { "Expected no registration data to be committed" }
    assert(storageController.restoreDecision == null) { "Expected no restore decision to have been made" }
  }

  @Test
  fun `choosing no recovery key returns to phone entry, and resubmitting the number verifies over sms instead of looping`() {
    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startManualRestore()
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS)
    enterPhoneNumber()

    // The user realizes they have no recovery key and abandons the restore
    waitForTag(TestTags.ENTER_AEP_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.ENTER_AEP_NO_KEY_BUTTON).performClick()

    // Resubmitting the same number verifies over SMS rather than routing back to recovery key entry
    resubmitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)
    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRegisterAccountRequest?.sessionId != null) { "Expected registration via a verified session but was ${networkController.lastRegisterAccountRequest}" }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(storageController.restoreDecision == RestoreDecision.NEW_ACCOUNT) { "Expected NEW_ACCOUNT restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `backing out of recovery key entry to the welcome screen and continuing verifies over sms instead of looping`() {
    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startManualRestore()
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS)
    enterPhoneNumber()
    waitForTag(TestTags.ENTER_AEP_SCREEN)

    // The user has no recovery key and backs all the way out to the welcome screen
    pressSystemBack()
    waitForTag(TestTags.PHONE_NUMBER_SCREEN)
    pressSystemBack()
    waitForTag(TestTags.ARCHIVE_RESTORE_SELECTION_SCREEN)
    pressSystemBack()

    // Continuing normally verifies over SMS rather than routing back into the abandoned restore
    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)
    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRegisterAccountRequest?.sessionId != null) { "Expected registration via a verified session but was ${networkController.lastRegisterAccountRequest}" }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(storageController.restoreDecision == RestoreDecision.NEW_ACCOUNT) { "Expected NEW_ACCOUNT restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `backing out of the local backup folder screen to the welcome screen and continuing verifies over sms instead of looping`() {
    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startManualRestore()
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FOLDER)
    enterPhoneNumber()

    // The folder screen is shown, but the user never selects a folder and backs all the way out to the welcome screen
    waitForTag(TestTags.LOCAL_BACKUP_RESTORE_SELECT_FOLDER_BUTTON)
    pressSystemBack()
    waitForTag(TestTags.PHONE_NUMBER_SCREEN)
    pressSystemBack()
    waitForTag(TestTags.ARCHIVE_RESTORE_SELECTION_SCREEN)
    pressSystemBack()

    // Continuing normally verifies over SMS rather than routing back to the folder screen
    submitPhoneNumber()
    submitVerificationCode(VERIFICATION_CODE)
    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRegisterAccountRequest?.sessionId != null) { "Expected registration via a verified session but was ${networkController.lastRegisterAccountRequest}" }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(storageController.restoreDecision == RestoreDecision.NEW_ACCOUNT) { "Expected NEW_ACCOUNT restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `restoring a local backup for a reglocked account whose reglock is not derived from the aep verifies the pin then restores`() {
    val aep = AccountEntropyPool.generate()
    val svrMasterKey = MasterKey(ByteArray(32) { it.toByte() })

    // The AEP is valid for the account (its recovery password is accepted), but the account's reglock is governed by a
    // separate master key held in SVR, so the reglock proof derived from the AEP is rejected.
    networkController.onRegisterAccount = { request ->
      when {
        request.registrationLock == svrMasterKey.deriveRegistrationLock() -> RequestResult.Success(networkController.registerAccountResponse(request.e164))
        else -> RequestResult.NonSuccess(
          RegisterAccountError.RegistrationLock(
            RegistrationLockResponse(
              timeRemaining = 14.days.inWholeMilliseconds,
              svr2Credentials = SvrCredentials(username = "svr-user", password = "svr-pass")
            )
          )
        )
      }
    }

    networkController.onRestoreMasterKeyFromSvr = { request ->
      if (request.pin == PIN) {
        RequestResult.Success(MasterKeyResponse(svrMasterKey))
      } else {
        RequestResult.NonSuccess(RestoreMasterKeyError.WrongPin(triesRemaining = 3))
      }
    }

    // The backup contains the user's PIN, so the flow finishes without any PIN screens after the restore
    storageController.onRestoreLocalBackupV2 = { _, _ ->
      flowOf(LocalBackupRestoreProgress.Complete(restoredSvrPin = PIN, restoredProfileKey = null))
    }

    var registrationComplete = false
    launchRegistrationFlow(folderPickerResult = backupFolderUri, onRegistrationComplete = { registrationComplete = true })

    startManualRestore()
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FOLDER)
    enterPhoneNumber()
    restoreLocalBackup(aep)

    // The AEP-derived reglock proof was rejected, so the user must prove their PIN to clear the registration lock
    waitForTag(TestTags.PIN_ENTRY_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_INPUT).performTextInput(PIN)
    composeTestRule.onNodeWithTag(TestTags.PIN_ENTRY_CONTINUE_BUTTON).performClick()

    // With the reglock cleared, the pending backup restore resumes on the post-registration local restore screen
    restoreFoundLocalBackup()

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRestoreMasterKeyRequest?.pin == PIN) { "Expected the PIN to be used to restore the master key from SVR" }
    assert(networkController.lastRegisterAccountRequest?.registrationLock == svrMasterKey.deriveRegistrationLock()) { "Expected registration with the reglock proof derived from the SVR master key" }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(storageController.restoreDecision == RestoreDecision.COMPLETED) { "Expected COMPLETED restore decision (the backup was restored) but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `entering a recovery key that cannot decrypt the local backup shows an inline error without registering`() {
    val aep = AccountEntropyPool.generate()

    storageController.onVerifyLocalBackupKey = { _, _ -> false }

    launchRegistrationFlow(folderPickerResult = backupFolderUri)

    startManualRestore()
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FOLDER)
    enterPhoneNumber()
    restoreLocalBackup(aep)

    // The key can't decrypt the backup, so submission is rejected inline before any registration attempt
    waitFor("the incorrect key to be rejected") {
      composeTestRule.onAllNodesWithTag(TestTags.ENTER_AEP_NEXT_BUTTON).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.Disabled) != null
    }
    assert(networkController.lastRegisterAccountRequest == null) { "Expected no registration attempt for a key that cannot decrypt the backup" }
  }

  @Test
  fun `quick restore with a remote backup completes registration`() {
    val aep = AccountEntropyPool.generate()

    // The old device scans the QR code as soon as it is shown and sends its provisioning data, including the PIN,
    // so no PIN screens are needed after the restore
    networkController.onStartProvisioning = {
      flowOf(
        ProvisioningEvent.QrCodeReady("https://signal.test/qr"),
        ProvisioningEvent.MessageReceived(networkController.provisioningMessage(aep = aep, e164 = E164, pin = PIN))
      )
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    // Registration happens automatically with the provisioned data, landing on restore selection
    startQuickRestore()

    // The provisioned AEP is already known, so the restore starts without the user re-entering anything
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS)
    startRemoteRestore()

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastSetRestoreMethodRequest?.method == RestoreMethod.REMOTE_BACKUP) {
      "Expected the old device to be notified of a remote backup restore but was ${networkController.lastSetRestoreMethodRequest}"
    }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(committed.accountEntropyPool == aep.value) { "Expected the committed AEP to be the provisioned one" }
    assert(committed.pin == PIN) { "Expected the provisioned pin $PIN but was ${committed.pin}" }
    assert(storageController.restoreDecision == RestoreDecision.COMPLETED) { "Expected COMPLETED restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `quick restore with a local backup completes registration`() {
    val aep = AccountEntropyPool.generate()

    // The old device has no remote backup plan, so only local backup, transfer, and skip are offered
    networkController.onStartProvisioning = {
      flowOf(
        ProvisioningEvent.QrCodeReady("https://signal.test/qr"),
        ProvisioningEvent.MessageReceived(networkController.provisioningMessage(aep = aep, e164 = E164, tier = null))
      )
    }

    var registrationComplete = false
    launchRegistrationFlow(folderPickerResult = backupFolderUri, onRegistrationComplete = { registrationComplete = true })

    startQuickRestore()

    // The provisioned AEP decrypts the backup automatically, so only the folder needs to be picked
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_BACKUP_FOLDER)
    restoreFoundLocalBackup()

    // The backup had no PIN in it, so the user creates one
    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastSetRestoreMethodRequest?.method == RestoreMethod.LOCAL_BACKUP) {
      "Expected the old device to be notified of a local backup restore but was ${networkController.lastSetRestoreMethodRequest}"
    }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(committed.accountEntropyPool == aep.value) { "Expected the committed AEP to be the provisioned one" }
    assert(committed.pin == PIN) { "Expected committed pin $PIN but was ${committed.pin}" }
  }

  @Test
  fun `quick restore can skip restoring and create a pin to complete registration`() {
    networkController.onStartProvisioning = {
      flowOf(
        ProvisioningEvent.QrCodeReady("https://signal.test/qr"),
        ProvisioningEvent.MessageReceived(networkController.provisioningMessage(aep = AccountEntropyPool.generate(), e164 = E164, tier = null))
      )
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startQuickRestore()

    // Decline the restore, confirming the skip warning. The old device did not provide a PIN, so the user creates one.
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_NONE)
    waitForTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    createPin(PIN)

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastSetRestoreMethodRequest?.method == RestoreMethod.DECLINE) {
      "Expected the old device to be notified of the declined restore but was ${networkController.lastSetRestoreMethodRequest}"
    }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(committed.pin == PIN) { "Expected committed pin $PIN but was ${committed.pin}" }
  }

  @Test
  fun `quick restore with a known pin can skip restoring and complete registration immediately`() {
    networkController.onStartProvisioning = {
      flowOf(
        ProvisioningEvent.QrCodeReady("https://signal.test/qr"),
        ProvisioningEvent.MessageReceived(networkController.provisioningMessage(aep = AccountEntropyPool.generate(), e164 = E164, tier = null, pin = PIN))
      )
    }

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startQuickRestore()

    // Decline the restore. The old device provided the PIN, so registration finishes with no further input.
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_NONE)
    waitForTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == E164) { "Expected committed e164 $E164 but was ${committed.accountData?.e164}" }
    assert(committed.pin == PIN) { "Expected the provisioned pin $PIN but was ${committed.pin}" }
    assert(storageController.restoreDecision == RestoreDecision.SKIPPED) { "Expected SKIPPED restore decision but was ${storageController.restoreDecision}" }
  }

  // -- Phone-numberless registration (Signal Login)

  @Test
  fun `happy path - registering without a phone number by buying a signal login, recording it, and typing it back`() {
    enableSignalLoginRegistration()

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startSignalLoginRegistration()
    buySignalLogin()

    // Redeeming the purchase registers the account outright, so the next thing the user sees is the login they now own
    waitForTag(TestTags.SIGNAL_LOGIN_INFO_SCREEN)
    val login = registeredSignalLogin()

    recordSignalLoginManually()
    enterSignalLogin(login)
    skipUsername()

    waitFor("registration to complete") { registrationComplete }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == null) { "Expected no committed e164 but was ${committed.accountData?.e164}" }
    assert(committed.accountData?.pni == null) { "Expected no committed PNI but was ${committed.accountData?.pni}" }
    assert(committed.accountData?.aci == login.aci.toString()) { "Expected committed ACI ${login.aci} but was ${committed.accountData?.aci}" }
    assert(committed.accountEntropyPool == login.aep.value) { "Expected the committed AEP to be the one the user was shown" }
    assert(committed.pin.isEmpty()) { "An account with no phone number has no PIN, but was ${committed.pin}" }
    assert(storageController.registrationFlowFinishedCount == 1) { "Expected the flow-finished hook to fire exactly once but fired ${storageController.registrationFlowFinishedCount} times" }
    assert(storageController.restoreDecision == RestoreDecision.NEW_ACCOUNT) { "Expected NEW_ACCOUNT restore decision but was ${storageController.restoreDecision}" }

    val request = networkController.lastRegisterAccountRequest
    assert(request != null) { "Expected a registration attempt" }
    assert(request!!.receiptCredentialPresentation != null) { "Expected the purchase to be redeemed as proof of payment but was $request" }
    assert(request.e164 == null && request.sessionId == null && request.recoveryPassword == null) { "Expected a registration carrying nothing but a receipt credential but was $request" }
    assert(request.pniPreKeys == null) { "An account with no phone number has no PNI, so no PNI key material should be sent" }
    assert(networkController.lastSetPinRequest == null) { "Should not have backed up a PIN for an account with no phone number" }
    assert(purchaseApi.consumedTokens == listOf(FakeOneTimePurchaseApi.PURCHASE_TOKEN)) { "Expected the purchase to be consumed once but was ${purchaseApi.consumedTokens}" }
  }

  @Test
  fun `a purchased signal login that the password manager takes and hands back moves the user on to the username step`() {
    enableSignalLoginRegistration()
    val savedCredentials = stubPasswordManager()

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startSignalLoginRegistration()
    buySignalLogin()

    waitForTag(TestTags.SIGNAL_LOGIN_INFO_SCREEN)
    val login = registeredSignalLogin()

    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_INFO_SAVE_TO_PASSWORD_MANAGER_BUTTON).performClick()

    // The password manager took the login, so the user is asked to confirm it really landed there
    waitForTag(TestTags.CONFIRM_LOGIN_SAVED_TO_PASSWORD_MANAGER_CONFIRM_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.CONFIRM_LOGIN_SAVED_TO_PASSWORD_MANAGER_CONFIRM_BUTTON).performClick()

    // What comes back is the login the user was given, so there is nothing left to do but pick a username
    skipUsername()

    waitFor("registration to complete") { registrationComplete }

    val expected = UsernamePasswordCredential(username = login.aci.toString().uppercase(), password = login.aep.displayValue)
    assert(savedCredentials == listOf(expected)) { "Expected the login the user was shown to be handed to the password manager once but was $savedCredentials" }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == null) { "Expected no committed e164 but was ${committed.accountData?.e164}" }
    assert(committed.accountEntropyPool == login.aep.value) { "Expected the committed AEP to be the one saved to the password manager" }
  }

  @Test
  fun `a login the password manager cannot hand back is not treated as saved, and can be recorded by hand instead`() {
    enableSignalLoginRegistration()
    stubPasswordManager()
    coEvery { SignalCredentialManager.getCredential(any(), any()) } returns null

    launchRegistrationFlow()

    startSignalLoginRegistration()
    buySignalLogin()

    waitForTag(TestTags.SIGNAL_LOGIN_INFO_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_INFO_SAVE_TO_PASSWORD_MANAGER_BUTTON).performClick()
    waitForTag(TestTags.CONFIRM_LOGIN_SAVED_TO_PASSWORD_MANAGER_CONFIRM_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.CONFIRM_LOGIN_SAVED_TO_PASSWORD_MANAGER_CONFIRM_BUTTON).performClick()

    // Nothing came back, so the user is warned rather than sent on believing their login is safe somewhere
    val context = ApplicationProvider.getApplicationContext<Application>()
    waitForText(context.getString(R.string.SignalLoginInfoScreen__your_signal_login_could_not_be_confirmed))
    assert(composeTestRule.onAllNodesWithTag(TestTags.ADD_USERNAME_SCREEN).fetchSemanticsNodes().isEmpty()) {
      "Expected to stay on the login info screen rather than moving on with an unconfirmed login"
    }

    // Recording it by hand is still open to them
    composeTestRule.onNodeWithText(context.getString(android.R.string.cancel)).performClick()
    waitForTag(TestTags.SIGNAL_LOGIN_INFO_SAVE_MANUALLY_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_INFO_SAVE_MANUALLY_BUTTON).performClick()

    waitForTag(TestTags.SIGNAL_LOGIN_MANUAL_SAVE_SCREEN)
  }

  @Test
  fun `typing back a login that is not the one the user was shown is rejected until the real one is entered`() {
    enableSignalLoginRegistration()

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startSignalLoginRegistration()
    buySignalLogin()

    waitForTag(TestTags.SIGNAL_LOGIN_INFO_SCREEN)
    val login = registeredSignalLogin()

    recordSignalLoginManually()

    // Some other perfectly well-formed login is still not the one they were just handed
    enterSignalLogin(SignalLogin(ACI.from(UUID.randomUUID()), AccountEntropyPool.generate()))

    waitForText(ApplicationProvider.getApplicationContext<Application>().getString(R.string.SignalLoginCredentialEntryScreen__that_doesnt_match_the_signal_login_you_were_shown))
    assert(composeTestRule.onAllNodesWithTag(TestTags.ADD_USERNAME_SCREEN).fetchSemanticsNodes().isEmpty()) {
      "Expected to stay on the confirmation step rather than accepting a login the user was never shown"
    }

    // Correcting it is accepted, and nothing was ever sent to the service to check it
    clearSignalLoginFields()
    enterSignalLogin(login)
    skipUsername()

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRegisterAccountRequest?.receiptCredentialPresentation != null) {
      "Confirming a recorded login is a local check, so the only registration should still be the purchase redemption"
    }
  }

  @Test
  fun `asking to see the login info again from the confirmation sheet returns to the keys`() {
    enableSignalLoginRegistration()
    launchRegistrationFlow()

    startSignalLoginRegistration()
    buySignalLogin()

    waitForTag(TestTags.SIGNAL_LOGIN_INFO_SAVE_MANUALLY_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_INFO_SAVE_MANUALLY_BUTTON).performClick()
    waitForTag(TestTags.SIGNAL_LOGIN_MANUAL_SAVE_CONTINUE_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_MANUAL_SAVE_CONTINUE_BUTTON).performClick()

    waitForTag(TestTags.CONFIRM_LOGIN_SAVED_SHOW_LOGIN_INFO_AGAIN_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.CONFIRM_LOGIN_SAVED_SHOW_LOGIN_INFO_AGAIN_BUTTON).performClick()

    waitFor("the confirmation sheet to close") {
      composeTestRule.onAllNodesWithTag(TestTags.CONFIRM_LOGIN_SAVED_SHEET).fetchSemanticsNodes().isEmpty()
    }
    assert(composeTestRule.onAllNodesWithTag(TestTags.SIGNAL_LOGIN_MANUAL_SAVE_SCREEN).fetchSemanticsNodes().isNotEmpty()) {
      "Expected the keys to still be on screen after backing out of the confirmation sheet"
    }
  }

  @Test
  fun `choosing a username after buying a signal login claims it and completes registration`() {
    enableSignalLoginRegistration()

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startSignalLoginRegistration()
    buySignalLogin()

    waitForTag(TestTags.SIGNAL_LOGIN_INFO_SCREEN)
    val login = registeredSignalLogin()

    recordSignalLoginManually()
    enterSignalLogin(login)

    waitForTag(TestTags.ADD_USERNAME_FIELD)
    composeTestRule.onNodeWithTag(TestTags.ADD_USERNAME_FIELD).performTextInput(USERNAME)
    waitForEnabledTag(TestTags.ADD_USERNAME_NEXT_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.ADD_USERNAME_NEXT_BUTTON).performClick()

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastReservedNickname == USERNAME) { "Expected a reservation for $USERNAME but was ${networkController.lastReservedNickname}" }
    assert(networkController.lastReservedDiscriminator == null) { "Expected the service to pick the discriminator but was ${networkController.lastReservedDiscriminator}" }
    assert(storageController.savedUsername == "$USERNAME.42") { "Expected the confirmed username to be saved but was ${storageController.savedUsername}" }
  }

  @Test
  fun `a receipt credential pasted into a debug build registers an account with no phone number`() {
    makeBuildDebuggable()
    enableSignalLoginRegistration(isGooglePlayBillingAvailable = false)

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startSignalLoginRegistration()

    waitForTag(TestTags.SIGNAL_LOGIN_PAYMENT_RECEIPT_CREDENTIAL_FIELD)
    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_PAYMENT_RECEIPT_CREDENTIAL_FIELD).performTextInput(issuedReceiptCredential())
    waitForEnabledTag(TestTags.SIGNAL_LOGIN_PAYMENT_CONTINUE_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_PAYMENT_CONTINUE_BUTTON).performClick()

    waitForTag(TestTags.SIGNAL_LOGIN_INFO_SCREEN)
    val login = registeredSignalLogin()

    recordSignalLoginManually()
    enterSignalLogin(login)
    skipUsername()

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRegisterAccountRequest?.receiptCredentialPresentation != null) {
      "Expected the pasted credential to be presented as proof of payment but was ${networkController.lastRegisterAccountRequest}"
    }
    assert(purchaseApi.launchCount == 0) { "Pasting a credential should bypass Google Play entirely" }
    assert(storageController.committedData?.accountData?.e164 == null) { "Expected an account with no phone number" }
  }

  @Test
  fun `logging in with an existing signal login reclaims the account and offers a restore, which can be skipped`() {
    enableSignalLoginRegistration()
    val login = signalLoginFor(reregistration = true)

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startSignalLoginRegistration()
    useExistingSignalLogin()
    enterSignalLogin(login)

    // The service knows the account, so the user is asked how they want to bring their data back
    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_NONE)
    waitForTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()

    waitFor("registration to complete") { registrationComplete }

    val request = networkController.lastRegisterAccountRequest
    assert(request != null) { "Expected a registration attempt" }
    assert(request!!.aci == login.aci) { "Expected the login to be reclaimed by ACI ${login.aci} but was $request" }
    assert(request.recoveryPassword == login.aep.deriveMasterKey().deriveRegistrationRecoveryPassword()) { "Expected the RRP derived from the entered recovery key but was $request" }
    assert(request.e164 == null && request.sessionId == null) { "Reclaiming a login needs neither a number nor a session but was $request" }

    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == null) { "Expected no committed e164 but was ${committed.accountData?.e164}" }
    assert(committed.accountData?.aci == login.aci.toString()) { "Expected committed ACI ${login.aci} but was ${committed.accountData?.aci}" }
    assert(committed.accountEntropyPool == login.aep.value) { "Expected the entered recovery key to become the account's AEP" }
    assert(committed.pin.isEmpty()) { "An account with no phone number has no PIN, but was ${committed.pin}" }
    assert(storageController.restoreDecision == RestoreDecision.SKIPPED) { "Expected SKIPPED restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `typing an account id into the phone number field goes straight to signal login entry with it filled in`() {
    enableSignalLoginRegistration()
    val login = signalLoginFor(reregistration = false)

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    goToPhoneNumberEntry()
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_PHONE_FIELD).performTextInput(login.aci.toString())
    waitForEnabledTag(TestTags.PHONE_NUMBER_NEXT_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_NEXT_BUTTON).performClick()

    // The account ID came along with the user, so only the recovery key is left to enter
    waitForTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_ENTRY_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_RECOVERY_KEY_FIELD).performTextInput(login.aep.value)
    submitSignalLogin()

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastCreateSessionE164 == null) { "An account ID is not a phone number, so no verification session should be created" }
    assert(networkController.lastRegisterAccountRequest?.aci == login.aci) { "Expected the typed account ID to be reclaimed but was ${networkController.lastRegisterAccountRequest}" }
    assert(storageController.restoreDecision == RestoreDecision.NEW_ACCOUNT) { "Expected NEW_ACCOUNT restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `a signal login the service rejects is flagged on both halves and can be corrected`() {
    enableSignalLoginRegistration()
    val login = signalLoginFor(reregistration = false)

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startSignalLoginRegistration()
    useExistingSignalLogin()

    // Either half could be the one that is wrong, so the pair is rejected as a whole
    enterSignalLogin(SignalLogin(login.aci, AccountEntropyPool.generate()))

    waitForText(ApplicationProvider.getApplicationContext<Application>().getString(R.string.SignalLoginCredentialEntryScreen__incorrect_account_id_or_recovery_key))
    assert(!registrationComplete) { "Registration should not complete with a login the service rejected" }

    clearSignalLoginFields()
    enterSignalLogin(login)

    waitFor("registration to complete") { registrationComplete }

    assert(composeTestRule.onAllNodesWithTag(TestTags.ARCHIVE_RESTORE_SELECTION_SCREEN).fetchSemanticsNodes().isEmpty()) {
      "The service reported a brand new account, so there should have been nothing to offer to restore"
    }
    assert(storageController.restoreDecision == RestoreDecision.NEW_ACCOUNT) { "Expected NEW_ACCOUNT restore decision but was ${storageController.restoreDecision}" }
  }

  @Test
  fun `a signal login that needs two-factor authentication is completed with a code from an authenticator app`() {
    enableSignalLoginRegistration()
    val totp = "654321"
    val login = signalLoginFor(reregistration = false, requiredTotp = totp.toInt())

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startSignalLoginRegistration()
    useExistingSignalLogin()
    enterSignalLogin(login)

    // The service wants a second factor, so the user picks one and enters a code from it
    waitForTag(TestTags.TWO_FACTOR_SELECTION_AUTHENTICATOR_APP_OPTION)
    composeTestRule.onNodeWithTag(TestTags.TWO_FACTOR_SELECTION_AUTHENTICATOR_APP_OPTION).performClick()
    waitForTag(TestTags.TOTP_ENTRY_DIGIT_0)
    composeTestRule.onNodeWithTag(TestTags.TOTP_ENTRY_DIGIT_0).performTextInput(totp)

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRegisterAccountRequest?.totp == totp.toInt()) { "Expected the entered code to be sent with the login but was ${networkController.lastRegisterAccountRequest}" }
    assert(storageController.committedData?.accountData?.e164 == null) { "Expected an account with no phone number" }
  }

  @Test
  fun `a reglocked signal login account is unlocked with the reglock derived from the entered recovery key`() {
    enableSignalLoginRegistration()
    val login = signalLoginFor(reregistration = false, registrationLocked = true)

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startSignalLoginRegistration()
    useExistingSignalLogin()
    enterSignalLogin(login)

    waitFor("registration to complete") { registrationComplete }

    assert(networkController.lastRegisterAccountRequest?.registrationLock == login.aep.deriveMasterKey().deriveRegistrationLock()) {
      "Expected the reglock derived from the recovery key to be retried automatically but was ${networkController.lastRegisterAccountRequest}"
    }
    assert(composeTestRule.onAllNodesWithTag(TestTags.PIN_ENTRY_SCREEN).fetchSemanticsNodes().isEmpty()) {
      "The recovery key already proves ownership, so the user should never have been asked for a PIN"
    }
  }

  @Test
  fun `restoring a remote backup after logging in with a signal login completes registration without a pin`() {
    enableSignalLoginRegistration()
    val login = signalLoginFor(reregistration = true)

    var registrationComplete = false
    launchRegistrationFlow(onRegistrationComplete = { registrationComplete = true })

    startSignalLoginRegistration()
    useExistingSignalLogin()
    enterSignalLogin(login)

    chooseRestoreOption(TestTags.ARCHIVE_RESTORE_SELECTION_FROM_SIGNAL_BACKUPS)
    startRemoteRestore()

    waitFor("registration to complete") { registrationComplete }

    assert(composeTestRule.onAllNodesWithTag(TestTags.PIN_CREATION_SCREEN).fetchSemanticsNodes().isEmpty()) {
      "An account with no phone number has no PIN, so PIN creation should never have been shown"
    }
    val committed = storageController.committedData
    assert(committed != null) { "Expected registration data to be committed" }
    assert(committed!!.accountData?.e164 == null) { "Expected no committed e164 but was ${committed.accountData?.e164}" }
    assert(committed.pin.isEmpty()) { "Expected no committed pin but was ${committed.pin}" }
    assert(storageController.restoreDecision == RestoreDecision.COMPLETED) { "Expected COMPLETED restore decision but was ${storageController.restoreDecision}" }
  }

  // -- Fixture helpers: configure the app and the fake service before the flow is launched.

  /**
   * Rebuilds the repository with phone-numberless registration turned on, which is what puts the Signal Login screens
   * in front of the user at all.
   */
  private fun enableSignalLoginRegistration(isGooglePlayBillingAvailable: Boolean = true) {
    repository = RegistrationRepository(
      context = ApplicationProvider.getApplicationContext<Application>(),
      networkController = networkController,
      storageController = storageController,
      isLinkAndSyncAvailable = false,
      isPhoneNumberlessRegistrationAvailable = true,
      isGooglePlayBillingAvailable = isGooglePlayBillingAvailable,
      signalLoginPurchaseApi = purchaseApi
    )
  }

  /** Marks the app debuggable, which is what gates the debug-only affordances in the flow. */
  private fun makeBuildDebuggable() {
    val applicationInfo = ApplicationProvider.getApplicationContext<Application>().applicationInfo
    applicationInfo.flags = applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
  }

  /**
   * Stands the system password manager up in memory: it takes whatever it is handed and gives back the last thing it
   * took. The returned list holds the credentials it has been asked to save, in order.
   */
  private fun stubPasswordManager(): List<UsernamePasswordCredential> {
    val saved = mutableListOf<UsernamePasswordCredential>()

    mockkObject(SignalCredentialManager)
    every { SignalCredentialManager.isSupported(any()) } returns true
    coEvery { SignalCredentialManager.saveCredential(any(), any(), any()) } answers {
      saved += UsernamePasswordCredential(username = secondArg(), password = thirdArg())
      CredentialManagerResult.Success
    }
    coEvery { SignalCredentialManager.getCredential(any(), any()) } answers { saved.lastOrNull() }

    return saved
  }

  /**
   * A Signal Login the fake service will honor, along with the [FakeNetworkController.onRegisterAccount] handler that
   * answers for the account behind it. Anything but the matching account ID and recovery key is rejected.
   *
   * @param reregistration Whether the service reports the login as reclaiming an account that already existed.
   * @param requiredTotp When set, the login is only accepted alongside this two-factor code.
   * @param registrationLocked Whether the account is registration locked until the reglock derived from the recovery key is provided.
   */
  private fun signalLoginFor(
    reregistration: Boolean,
    requiredTotp: Int? = null,
    registrationLocked: Boolean = false
  ): SignalLogin {
    val login = SignalLogin(ACI.from(UUID.randomUUID()), AccountEntropyPool.generate())
    val masterKey = login.aep.deriveMasterKey()

    networkController.onRegisterAccount = { request ->
      when {
        request.aci != login.aci || request.recoveryPassword != masterKey.deriveRegistrationRecoveryPassword() -> {
          RequestResult.NonSuccess(RegisterAccountError.RegistrationRecoveryPasswordIncorrect("no such login"))
        }
        requiredTotp != null && request.totp != requiredTotp -> {
          RequestResult.NonSuccess(RegisterAccountError.TotpMissingOrIncorrect)
        }
        registrationLocked && request.registrationLock != masterKey.deriveRegistrationLock() -> {
          RequestResult.NonSuccess(
            RegisterAccountError.RegistrationLock(
              RegistrationLockResponse(
                timeRemaining = 14.days.inWholeMilliseconds,
                svr2Credentials = SvrCredentials(username = "svr-user", password = "svr-pass")
              )
            )
          )
        }
        else -> RequestResult.Success(networkController.registerAccountResponse(e164 = null, reregistration = reregistration, aci = login.aci.toString()))
      }
    }

    return login
  }

  /** A base64 receipt credential issued by the fake service, in the form a debug build lets the user paste one in. */
  private fun issuedReceiptCredential(): String {
    val requestContext = networkController.createReceiptCredentialRequestContext()
    val issued = networkController.issueLoginReceiptCredential(requestContext.request) as CreateLoginReceiptCredentialResult.Issued
    val received = networkController.receiveReceiptCredential(requestContext, issued.receiptCredentialResponse) as ReceiptCredentialResult.Success

    return Base64.encodeWithPadding(received.value.serialize())
  }

  /** The Signal Login the flow has just registered, read out of the shared state so a test can act on it as the user would. */
  private fun registeredSignalLogin(): SignalLogin {
    val state = viewModel.state.value
    val aci = state.aci
    val aep = state.accountEntropyPool
    assert(aci != null && aep != null) { "Expected the flow to be holding a registered Signal Login but was $state" }

    return SignalLogin(aci!!, aep!!)
  }

  /** Both halves of a Signal Login: the account ID the user types, and the recovery key that pairs with it. */
  private data class SignalLogin(val aci: ACI, val aep: AccountEntropyPool)

  // -- Flow helpers: each one drives the UI from the screen the flow is currently on.

  /**
   * @param folderPickerResult When set, any system activity launched for a result (i.e. the backup folder picker)
   *   is immediately answered with this value.
   */
  private fun launchRegistrationFlow(
    folderPickerResult: Uri? = null,
    onRegistrationComplete: () -> Unit = {}
  ) {
    // Created here rather than in setup() so that its async init cannot read controller state before the test has finished configuring it
    viewModel = RegistrationViewModel(repository, SavedStateHandle())

    composeTestRule.setContent {
      backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
      SignalTheme {
        ActivityResultInterceptor(folderPickerResult) {
          RegistrationNavHost(
            registrationViewModel = viewModel,
            permissionsState = createMockPermissionsState(),
            onRegistrationComplete = onRegistrationComplete
          )
        }
      }
    }
  }

  @Composable
  private fun ActivityResultInterceptor(result: Any?, content: @Composable () -> Unit) {
    if (result == null) {
      content()
    } else {
      val owner = remember { ImmediateResultRegistryOwner(result) }
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
        content()
      }
    }
  }

  /** From the Welcome screen: continues to phone number entry (permissions are granted, so that screen is skipped), enters [PHONE_NUMBER], and confirms the dialog. */
  private fun submitPhoneNumber() {
    goToPhoneNumberEntry()
    enterPhoneNumber()
  }

  /** From the Welcome screen: continues to phone number entry, where either a number or an account ID can be entered. */
  private fun goToPhoneNumberEntry() {
    waitForTag(TestTags.WELCOME_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.WELCOME_GET_STARTED_BUTTON).performClick()
    waitForTag(TestTags.PHONE_NUMBER_SCREEN)
  }

  /** From the phone number entry screen: enters [PHONE_NUMBER] and confirms the dialog. */
  private fun enterPhoneNumber() {
    waitForTag(TestTags.PHONE_NUMBER_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_PHONE_FIELD).performTextInput(PHONE_NUMBER)
    resubmitPhoneNumber()
  }

  /** From the phone number entry screen with the number already filled in: taps next and confirms the dialog. */
  private fun resubmitPhoneNumber() {
    waitForTag(TestTags.PHONE_NUMBER_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_NEXT_BUTTON).performClick()

    waitForTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()
  }

  /** Simulates the system back button by dispatching through the activity's back dispatcher, the same path a real back press takes into the NavDisplay. */
  private fun pressSystemBack() {
    composeTestRule.runOnUiThread { backDispatcher!!.onBackPressed() }
  }

  /** From the Welcome screen: navigates to manual restore selection via "restore or transfer" → "don't have my old phone". */
  private fun startManualRestore() {
    waitForTag(TestTags.WELCOME_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.WELCOME_RESTORE_OR_TRANSFER_BUTTON).performClick()
    waitForTag(TestTags.WELCOME_RESTORE_NO_OLD_PHONE_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.WELCOME_RESTORE_NO_OLD_PHONE_BUTTON).performClick()
  }

  /**
   * From the Welcome screen: starts a quick restore via "restore or transfer" → "have my old phone", which shows the
   * QR code that the (fake) old device immediately scans.
   */
  private fun startQuickRestore() {
    waitForTag(TestTags.WELCOME_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.WELCOME_RESTORE_OR_TRANSFER_BUTTON).performClick()
    waitForTag(TestTags.WELCOME_RESTORE_HAS_OLD_PHONE_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.WELCOME_RESTORE_HAS_OLD_PHONE_BUTTON).performClick()
  }

  /** From the archive restore selection screen: picks the restore option with the given tag. */
  private fun chooseRestoreOption(optionTag: String) {
    waitForTag(TestTags.ARCHIVE_RESTORE_SELECTION_SCREEN)
    composeTestRule.onNodeWithTag(optionTag).performClick()
  }

  /** From the AEP entry screen: types the backup key and submits it. */
  private fun enterAep(aep: AccountEntropyPool) {
    waitForTag(TestTags.ENTER_AEP_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.ENTER_AEP_INPUT).performTextInput(aep.value)
    composeTestRule.onNodeWithTag(TestTags.ENTER_AEP_NEXT_BUTTON).performClick()
  }

  /** From the local backup restore screen: picks the backup folder (answered by the fake folder picker) and restores the backup that is found. */
  private fun restoreFoundLocalBackup() {
    waitForTag(TestTags.LOCAL_BACKUP_RESTORE_SELECT_FOLDER_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.LOCAL_BACKUP_RESTORE_SELECT_FOLDER_BUTTON).performClick()
    waitForTag(TestTags.LOCAL_BACKUP_RESTORE_RESTORE_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.LOCAL_BACKUP_RESTORE_RESTORE_BUTTON).performClick()
  }

  /** [restoreFoundLocalBackup], then decrypts it by entering [aep] when prompted. */
  private fun restoreLocalBackup(aep: AccountEntropyPool) {
    restoreFoundLocalBackup()
    enterAep(aep)
  }

  /** From the remote restore screen: starts the restore once the backup info has loaded. */
  private fun startRemoteRestore() {
    waitForTag(TestTags.REMOTE_BACKUP_RESTORE_RESTORE_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.REMOTE_BACKUP_RESTORE_RESTORE_BUTTON).performClick()
  }

  /**
   * From the captcha screen: simulates the user solving the captcha by driving the WebView's client with the
   * `signalcaptcha://` redirect that a real solve produces.
   */
  @Suppress("DEPRECATION")
  private fun solveCaptcha(token: String) {
    var webView: WebView? = null
    // Matched with onAllNodesWithTag so that an absent screen fails the condition and is retried, rather than throwing out of the wait
    waitFor("the captcha WebView") {
      val composeView = (composeTestRule.onAllNodesWithTag(TestTags.CAPTCHA_SCREEN).fetchSemanticsNodes().firstOrNull()?.root as? ViewRootForTest)?.view
      webView = composeView?.let { findWebView(it) }
      webView != null
    }
    webView!!.let { Shadows.shadowOf(it).webViewClient.shouldOverrideUrlLoading(it, "signalcaptcha://$token") }
  }

  private fun findWebView(view: View): WebView? {
    if (view is WebView) {
      return view
    }
    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        findWebView(view.getChildAt(i))?.let { return it }
      }
    }
    return null
  }

  /** From the verification code screen: enters all six digits of [code], which submits automatically. */
  private fun submitVerificationCode(code: String) {
    waitForTag(TestTags.VERIFICATION_CODE_DIGIT_0)
    composeTestRule.onNodeWithTag(TestTags.VERIFICATION_CODE_DIGIT_0).performTextInput(code)
  }

  /** From the PIN creation screen: enters [pin], then re-enters it on the confirmation step. */
  private fun createPin(pin: String) {
    waitForTag(TestTags.PIN_CREATION_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_INPUT).performTextInput(pin)
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_NEXT_BUTTON).performClick()

    // Wait for the confirm step's fresh input field to fully replace the create step's
    waitFor("PIN confirmation step") {
      composeTestRule.onAllNodesWithTag(TestTags.PIN_CREATION_CONFIRM_INPUT).fetchSemanticsNodes().isNotEmpty() &&
        composeTestRule.onAllNodesWithTag(TestTags.PIN_CREATION_INPUT).fetchSemanticsNodes().isEmpty()
    }
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_CONFIRM_INPUT).performTextInput(pin)
    composeTestRule.onNodeWithTag(TestTags.PIN_CREATION_NEXT_BUTTON).performClick()
  }

  /** From the Welcome screen: continues to phone number entry, then opts to register without a phone number. */
  private fun startSignalLoginRegistration() {
    goToPhoneNumberEntry()
    composeTestRule.onNodeWithTag(TestTags.PHONE_NUMBER_REGISTER_WITHOUT_NUMBER_BUTTON).performClick()
  }

  /** From the Signal Login payment screen: buys a login, which registers an account with no phone number. */
  private fun buySignalLogin() {
    waitForTag(TestTags.SIGNAL_LOGIN_PAYMENT_SCREEN)
    waitForEnabledTag(TestTags.SIGNAL_LOGIN_PAYMENT_CONTINUE_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_PAYMENT_CONTINUE_BUTTON).performClick()
  }

  /** From the Signal Login payment screen: says the user already owns a login, which leads to credential entry. */
  private fun useExistingSignalLogin() {
    waitForTag(TestTags.SIGNAL_LOGIN_PAYMENT_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_PAYMENT_EXISTING_LOGIN_OPTION).performScrollTo().performClick()
    waitForEnabledTag(TestTags.SIGNAL_LOGIN_PAYMENT_CONTINUE_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_PAYMENT_CONTINUE_BUTTON).performClick()
  }

  /** From the Signal Login info screen: records the login by hand, then says on the sheet that it really is recorded. */
  private fun recordSignalLoginManually() {
    waitForTag(TestTags.SIGNAL_LOGIN_INFO_SAVE_MANUALLY_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_INFO_SAVE_MANUALLY_BUTTON).performClick()
    waitForTag(TestTags.SIGNAL_LOGIN_MANUAL_SAVE_CONTINUE_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_MANUAL_SAVE_CONTINUE_BUTTON).performClick()
    waitForTag(TestTags.CONFIRM_LOGIN_SAVED_CONTINUE_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.CONFIRM_LOGIN_SAVED_CONTINUE_BUTTON).performClick()
  }

  /** On the Signal Login credential entry screen: fills both halves of [login] in and submits them. */
  private fun enterSignalLogin(login: SignalLogin) {
    waitForTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_ENTRY_SCREEN)
    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_ACCOUNT_ID_FIELD).performTextInput(login.aci.toString())
    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_RECOVERY_KEY_FIELD).performTextInput(login.aep.value)
    submitSignalLogin()
  }

  /** On the Signal Login credential entry screen with both halves already filled in: submits them. */
  private fun submitSignalLogin() {
    waitForEnabledTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_NEXT_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_NEXT_BUTTON).performClick()
  }

  /** On the Signal Login credential entry screen: empties both fields so a rejected login can be typed again. */
  private fun clearSignalLoginFields() {
    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_ACCOUNT_ID_FIELD).performTextClearance()
    composeTestRule.onNodeWithTag(TestTags.SIGNAL_LOGIN_CREDENTIAL_RECOVERY_KEY_FIELD).performTextClearance()
  }

  /** From the add-username screen: declines to pick a username, confirming the warning that comes with it. */
  private fun skipUsername() {
    waitForTag(TestTags.ADD_USERNAME_SKIP_BUTTON)
    composeTestRule.onNodeWithTag(TestTags.ADD_USERNAME_SKIP_BUTTON).performClick()
    waitForTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
    composeTestRule.onNodeWithTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON).performClick()
  }

  /**
   * Waits for [condition] to become true, pumping the main looper so that work scheduled by coroutines resuming from
   * background dispatchers (the real repository hops through Dispatchers.IO) gets executed. Advancing the looper clock
   * also completes animations and any short delay-based timeouts in the flow.
   */
  private fun waitFor(description: String, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS
    while (true) {
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
      composeTestRule.waitForIdle()
      if (condition()) {
        return
      }
      if (System.currentTimeMillis() > deadline) {
        throw AssertionError("Timed out waiting for $description")
      }
      Thread.sleep(10)
    }
  }

  private fun waitForTag(tag: String) {
    waitFor("node with tag $tag") {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  /** Waits for the node with [tag] to be present and enabled, since a screen often renders its action button before the data that makes it usable has loaded. */
  private fun waitForEnabledTag(tag: String) {
    waitFor("node with tag $tag to be enabled") {
      val node = composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()
      node != null && node.config.getOrNull(SemanticsProperties.Disabled) == null
    }
  }

  private fun waitForText(text: String) {
    waitFor("node with text $text") {
      composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun preExistingRegistrationData(e164: String): PreExistingRegistrationData {
    return PreExistingRegistrationData(
      e164 = e164,
      aci = ACI.from(UUID.randomUUID()),
      pni = PNI.from(UUID.randomUUID()),
      servicePassword = "service-password",
      aep = AccountEntropyPool.generate(),
      registrationLockEnabled = false,
      unrestrictedUnidentifiedAccess = false,
      aciIdentityKeyPair = IdentityKeyPair.generate(),
      pniIdentityKeyPair = IdentityKeyPair.generate()
    )
  }

  private fun createMockPermissionsState(): MockMultiplePermissionsState {
    return MockMultiplePermissionsState(
      allPermissionsGranted = true,
      permissions = RegistrationPermissions.getRequiredPermissions(isModernBackupDirectorySelectionRequired = false).map { MockPermissionsState(it) }
    )
  }
}

/**
 * An [ActivityResultRegistryOwner] that immediately answers any launched contract (e.g. the system folder picker)
 * with [result], since no real activity can handle intents in a unit test.
 */
private class ImmediateResultRegistryOwner(private val result: Any?) : ActivityResultRegistryOwner {
  override val activityResultRegistry = object : ActivityResultRegistry() {
    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
      dispatchResult(requestCode, result)
    }
  }
}
