/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.signal.appsettings.dataandstorage.AutoDownloadMediaType
import org.signal.appsettings.dataandstorage.AutoDownloadNetwork
import org.signal.appsettings.dataandstorage.DataAndStorageSettingsAction
import org.signal.appsettings.dataandstorage.DataAndStorageSettingsEvent
import org.signal.appsettings.dataandstorage.DataAndStorageSettingsState
import org.signal.appsettings.dataandstorage.DataAndStorageSettingsState.Dialog
import org.signal.core.models.calls.CallDataMode
import org.signal.core.models.media.SentMediaQuality
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class DataAndStorageSettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  private val storageUse = MutableStateFlow<Long?>(null)
  private val autoDownload = mutableMapOf<AutoDownloadNetwork, Set<AutoDownloadMediaType>>()
  private var sentMediaQuality = SentMediaQuality.STANDARD
  private var callDataMode = CallDataMode.HIGH_ALWAYS
  private var forceWebsocket = false

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(DataAndStorageSettingsRepository)

    every { DataAndStorageSettingsRepository.totalStorageUseBytes() } returns storageUse
    every { DataAndStorageSettingsRepository.refreshStorageUsage() } just Runs
    every { DataAndStorageSettingsRepository.getAutoDownload(any()) } answers { autoDownload[firstArg()].orEmpty() }
    coEvery { DataAndStorageSettingsRepository.setAutoDownload(any(), any()) } answers { autoDownload[firstArg()] = secondArg() }
    every { DataAndStorageSettingsRepository.getAlwaysAutoDownloadedBytes() } returns 102_400
    every { DataAndStorageSettingsRepository.getSentMediaQuality() } answers { sentMediaQuality }
    coEvery { DataAndStorageSettingsRepository.setSentMediaQuality(any()) } answers { sentMediaQuality = firstArg() }
    every { DataAndStorageSettingsRepository.getCallDataMode() } answers { callDataMode }
    coEvery { DataAndStorageSettingsRepository.setCallDataMode(any()) } answers { callDataMode = firstArg() }
    every { DataAndStorageSettingsRepository.isForceWebsocketModeEnabled() } answers { forceWebsocket }
    coEvery { DataAndStorageSettingsRepository.setForceWebsocketModeEnabled(any()) } answers { forceWebsocket = firstArg() }
    every { DataAndStorageSettingsRepository.isPlayServicesAvailable() } returns true
    every { DataAndStorageSettingsRepository.isProxyEnabled() } returns false
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkObject(DataAndStorageSettingsRepository)
  }

  @Test
  fun `initial state is read out of the repository`() = runTest(testDispatcher) {
    autoDownload[AutoDownloadNetwork.WIFI] = setOf(AutoDownloadMediaType.IMAGES, AutoDownloadMediaType.VIDEO)
    sentMediaQuality = SentMediaQuality.HIGH
    callDataMode = CallDataMode.LOW_ALWAYS
    forceWebsocket = true
    every { DataAndStorageSettingsRepository.isProxyEnabled() } returns true

    val viewModel = DataAndStorageSettingsViewModel()

    assertThat(viewModel.state.value).isEqualTo(
      DataAndStorageSettingsState(
        autoDownload = mapOf(
          AutoDownloadNetwork.MOBILE to emptySet(),
          AutoDownloadNetwork.WIFI to setOf(AutoDownloadMediaType.IMAGES, AutoDownloadMediaType.VIDEO),
          AutoDownloadNetwork.ROAMING to emptySet()
        ),
        alwaysAutoDownloadedBytes = 102_400,
        sentMediaQuality = SentMediaQuality.HIGH,
        callDataMode = CallDataMode.LOW_ALWAYS,
        forceWebsocketMode = true,
        isProxyEnabled = true
      )
    )
  }

  @Test
  fun `storage usage is collected as it is calculated`() = runTest(testDispatcher) {
    val viewModel = DataAndStorageSettingsViewModel()

    storageUse.value = 1_000

    assertThat(viewModel.state.value.totalStorageUseBytes).isEqualTo(1_000L)
  }

  @Test
  fun `ScreenResumed re-reads settings and recalculates storage usage`() = runTest(testDispatcher) {
    val viewModel = DataAndStorageSettingsViewModel()
    every { DataAndStorageSettingsRepository.isProxyEnabled() } returns true

    viewModel.onEvent(DataAndStorageSettingsEvent.ScreenResumed)

    assertThat(viewModel.state.value.isProxyEnabled).isTrue()
    verify { DataAndStorageSettingsRepository.refreshStorageUsage() }
  }

  @Test
  fun `navigation events emit the matching actions`() = runTest(testDispatcher) {
    val viewModel = DataAndStorageSettingsViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(DataAndStorageSettingsEvent.NavigateBackClicked)
    viewModel.onEvent(DataAndStorageSettingsEvent.ManageStorageClicked)
    viewModel.onEvent(DataAndStorageSettingsEvent.UseProxyClicked)

    assertThat(actions).containsExactly(
      DataAndStorageSettingsAction.NavigateBack,
      DataAndStorageSettingsAction.NavigateToManageStorage,
      DataAndStorageSettingsAction.NavigateToProxy
    )
  }

  @Test
  fun `AutoDownloadChanged stores the selection for that network only`() = runTest(testDispatcher) {
    val viewModel = DataAndStorageSettingsViewModel()

    viewModel.onEvent(DataAndStorageSettingsEvent.AutoDownloadChanged(AutoDownloadNetwork.ROAMING, setOf(AutoDownloadMediaType.AUDIO)))

    coVerify { DataAndStorageSettingsRepository.setAutoDownload(AutoDownloadNetwork.ROAMING, setOf(AutoDownloadMediaType.AUDIO)) }
    assertThat(viewModel.state.value.autoDownloadFor(AutoDownloadNetwork.ROAMING)).isEqualTo(setOf(AutoDownloadMediaType.AUDIO))
    assertThat(viewModel.state.value.autoDownloadFor(AutoDownloadNetwork.WIFI)).isEqualTo(emptySet())
  }

  @Test
  fun `SentMediaQualitySelected stores the quality`() = runTest(testDispatcher) {
    val viewModel = DataAndStorageSettingsViewModel()

    viewModel.onEvent(DataAndStorageSettingsEvent.SentMediaQualitySelected(SentMediaQuality.HIGH))

    coVerify { DataAndStorageSettingsRepository.setSentMediaQuality(SentMediaQuality.HIGH) }
    assertThat(viewModel.state.value.sentMediaQuality).isEqualTo(SentMediaQuality.HIGH)
  }

  @Test
  fun `CallDataModeSelected stores the mode`() = runTest(testDispatcher) {
    val viewModel = DataAndStorageSettingsViewModel()

    viewModel.onEvent(DataAndStorageSettingsEvent.CallDataModeSelected(CallDataMode.HIGH_ON_WIFI))

    coVerify { DataAndStorageSettingsRepository.setCallDataMode(CallDataMode.HIGH_ON_WIFI) }
    assertThat(viewModel.state.value.callDataMode).isEqualTo(CallDataMode.HIGH_ON_WIFI)
  }

  @Test
  fun `turning on stay connected in background asks for confirmation first`() = runTest(testDispatcher) {
    val viewModel = DataAndStorageSettingsViewModel()

    viewModel.onEvent(DataAndStorageSettingsEvent.StayConnectedInBackgroundToggled(true))

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.ConfirmStayConnectedInBackground)
    coVerify(exactly = 0) { DataAndStorageSettingsRepository.setForceWebsocketModeEnabled(any()) }
  }

  @Test
  fun `confirming stay connected in background enables it and closes the dialog`() = runTest(testDispatcher) {
    val viewModel = DataAndStorageSettingsViewModel()

    viewModel.onEvent(DataAndStorageSettingsEvent.StayConnectedInBackgroundToggled(true))
    viewModel.onEvent(DataAndStorageSettingsEvent.StayConnectedInBackgroundConfirmed)

    coVerify { DataAndStorageSettingsRepository.setForceWebsocketModeEnabled(true) }
    assertThat(viewModel.state.value.forceWebsocketMode).isTrue()
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `StayConnectedInBackgroundConfirmed is ignored without the confirmation dialog`() = runTest(testDispatcher) {
    val viewModel = DataAndStorageSettingsViewModel()

    viewModel.onEvent(DataAndStorageSettingsEvent.StayConnectedInBackgroundConfirmed)

    coVerify(exactly = 0) { DataAndStorageSettingsRepository.setForceWebsocketModeEnabled(any()) }
  }

  @Test
  fun `turning off stay connected in background disables it without asking`() = runTest(testDispatcher) {
    forceWebsocket = true
    val viewModel = DataAndStorageSettingsViewModel()

    viewModel.onEvent(DataAndStorageSettingsEvent.StayConnectedInBackgroundToggled(false))

    coVerify { DataAndStorageSettingsRepository.setForceWebsocketModeEnabled(false) }
    assertThat(viewModel.state.value.forceWebsocketMode).isFalse()
    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
  }

  @Test
  fun `DialogDismissed closes the dialog without changing anything`() = runTest(testDispatcher) {
    val viewModel = DataAndStorageSettingsViewModel()

    viewModel.onEvent(DataAndStorageSettingsEvent.StayConnectedInBackgroundToggled(true))
    viewModel.onEvent(DataAndStorageSettingsEvent.DialogDismissed)

    assertThat(viewModel.state.value.dialog).isEqualTo(Dialog.None)
    coVerify(exactly = 0) { DataAndStorageSettingsRepository.setForceWebsocketModeEnabled(any()) }
  }

  private fun TestScope.collectActions(actions: Flow<DataAndStorageSettingsAction>): List<DataAndStorageSettingsAction> {
    val collected = mutableListOf<DataAndStorageSettingsAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
