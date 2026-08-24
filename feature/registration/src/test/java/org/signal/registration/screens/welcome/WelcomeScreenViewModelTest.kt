/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.welcome

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.registration.PreExistingRegistrationData
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationFlowState
import org.signal.registration.RegistrationRepository
import org.signal.registration.RegistrationRoute

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeScreenViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private lateinit var mockRepository: RegistrationRepository
  private lateinit var emittedParentEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var emittedStates: MutableList<WelcomeScreenState>
  private lateinit var stateEmitter: (WelcomeScreenState) -> Unit

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    mockRepository = mockk(relaxed = true)
    every { mockRepository.isLinkAndSyncAvailable } returns false
    emittedParentEvents = mutableListOf()
    parentEventEmitter = { event -> emittedParentEvents.add(event) }
    emittedStates = mutableListOf()
    stateEmitter = { state -> emittedStates.add(state) }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `while the parent flow is still loading the restore or transfer option stays hidden`() {
    val viewModel = createViewModel()

    viewModel.applyEvent(WelcomeScreenState(showRestoreOrTransfer = false), WelcomeScreenEvents.ParentStateChanged(RegistrationFlowState(isRestoringNavigationState = true)), parentEventEmitter, stateEmitter)

    assertThat(emittedStates.last().showRestoreOrTransfer).isFalse()
  }

  @Test
  fun `once loaded with no pre-existing data the restore or transfer option is shown`() {
    val viewModel = createViewModel()

    viewModel.applyEvent(WelcomeScreenState(showRestoreOrTransfer = false), WelcomeScreenEvents.ParentStateChanged(RegistrationFlowState(isRestoringNavigationState = false, preExistingRegistrationData = null)), parentEventEmitter, stateEmitter)

    assertThat(emittedStates.last().showRestoreOrTransfer).isTrue()
  }

  @Test
  fun `once loaded with pre-existing data the restore or transfer option stays hidden`() {
    val viewModel = createViewModel()

    viewModel.applyEvent(WelcomeScreenState(showRestoreOrTransfer = false), WelcomeScreenEvents.ParentStateChanged(RegistrationFlowState(isRestoringNavigationState = false, preExistingRegistrationData = mockk<PreExistingRegistrationData>(relaxed = true))), parentEventEmitter, stateEmitter)

    assertThat(emittedStates.last().showRestoreOrTransfer).isFalse()
  }

  @Test
  fun `Continue navigates straight to phone number entry when permissions are granted`() {
    val viewModel = createViewModel(hasPermissions = true)

    viewModel.applyEvent(WelcomeScreenState(), WelcomeScreenEvents.Continue, parentEventEmitter, stateEmitter)

    assertThat(emittedParentEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.PhoneNumberEntry)
  }

  @Test
  fun `Continue routes through the permissions screen when permissions are missing`() {
    val viewModel = createViewModel(hasPermissions = false)

    viewModel.applyEvent(WelcomeScreenState(), WelcomeScreenEvents.Continue, parentEventEmitter, stateEmitter)

    assertThat(emittedParentEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.Permissions(nextRoute = RegistrationRoute.PhoneNumberEntry))
  }

  @Test
  fun `HasOldPhone navigates to the quick restore scan when permissions are granted`() {
    val viewModel = createViewModel(hasPermissions = true)

    viewModel.applyEvent(WelcomeScreenState(), WelcomeScreenEvents.HasOldPhone, parentEventEmitter, stateEmitter)

    assertThat(emittedParentEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.QuickRestoreQrScan)
  }

  @Test
  fun `DoesNotHaveOldPhone navigates to the manual restore selection when permissions are granted`() {
    val viewModel = createViewModel(hasPermissions = true)

    viewModel.applyEvent(WelcomeScreenState(), WelcomeScreenEvents.DoesNotHaveOldPhone, parentEventEmitter, stateEmitter)

    assertThat(emittedParentEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isEqualTo(RegistrationRoute.ArchiveRestoreSelection.forManualRestore())
  }

  @Test
  fun `LinkDevice navigates straight to link account when no linked device permission is required`() {
    val viewModel = createViewModel(requiredLinkedDevicePermission = null)

    viewModel.applyEvent(WelcomeScreenState(), WelcomeScreenEvents.LinkDevice, parentEventEmitter, stateEmitter)

    assertThat(emittedParentEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.LinkAccount>()
  }

  @Test
  fun `LinkDevice routes through allow notifications when a linked device permission is required`() {
    val viewModel = createViewModel(requiredLinkedDevicePermission = "android.permission.POST_NOTIFICATIONS")

    viewModel.applyEvent(WelcomeScreenState(), WelcomeScreenEvents.LinkDevice, parentEventEmitter, stateEmitter)

    assertThat(emittedParentEvents.last())
      .isInstanceOf<RegistrationFlowEvent.NavigateToScreen>()
      .prop(RegistrationFlowEvent.NavigateToScreen::route)
      .isInstanceOf<RegistrationRoute.AllowNotifications>()
  }

  @Test
  fun `ViewTermsAndPrivacy emits an action to open the terms and privacy page`() = runTest(testDispatcher) {
    val viewModel = createViewModel()
    val actions = collectActions(viewModel)

    viewModel.applyEvent(WelcomeScreenState(), WelcomeScreenEvents.ViewTermsAndPrivacy, parentEventEmitter, stateEmitter)

    assertThat(actions).containsExactly(WelcomeScreenActions.ViewTermsAndPrivacy)
  }

  private fun TestScope.collectActions(viewModel: WelcomeScreenViewModel): List<WelcomeScreenActions> {
    val actions = mutableListOf<WelcomeScreenActions>()
    backgroundScope.launch(testDispatcher) { viewModel.actions.collect { actions.add(it) } }
    return actions
  }

  private fun createViewModel(
    parentState: RegistrationFlowState = RegistrationFlowState(),
    hasPermissions: Boolean = true,
    requiredLinkedDevicePermission: String? = null
  ): WelcomeScreenViewModel {
    return WelcomeScreenViewModel(mockRepository, MutableStateFlow(parentState), parentEventEmitter, hasPermissions = { hasPermissions }, getRequiredLinkedDevicePermission = { requiredLinkedDevicePermission })
  }
}
