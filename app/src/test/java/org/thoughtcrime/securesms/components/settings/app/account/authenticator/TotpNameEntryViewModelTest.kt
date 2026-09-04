/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.authenticator

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import org.signal.appsettings.totp.TotpApp
import org.signal.appsettings.totpnameentry.TotpNameEntryAction
import org.signal.appsettings.totpnameentry.TotpNameEntryEvent
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class TotpNameEntryViewModelTest {

  companion object {
    private val EXISTING_APP = TotpApp(id = 7, name = "Twilio Authy", createdAt = 0)

    /** The id the service assigned when the code was confirmed, before the app had a name. */
    private const val NEW_APP_ID = 1L
  }

  private val testDispatcher = UnconfinedTestDispatcher()
  private val repository: TotpRepository = mockk(relaxed = true)

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    every { repository.getMaxApps() } returns 2
    coEvery { repository.nameNewTotpApp(any(), any()) } returns TotpRepository.UpdateResult.Success
    coEvery { repository.renameTotpApp(any(), any()) } returns TotpRepository.UpdateResult.Success
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `naming a new app starts empty and isn't renaming`() = runTest(testDispatcher) {
    val viewModel = createViewModel(appId = NEW_APP_ID)

    assertThat(viewModel.state.value.name).isEqualTo("")
    assertThat(viewModel.state.value.renaming).isFalse()
  }

  @Test
  fun `renaming starts from the app's current name`() = runTest(testDispatcher) {
    val viewModel = createViewModel(appId = EXISTING_APP.id, renamedApp = EXISTING_APP)

    assertThat(viewModel.state.value.name).isEqualTo(EXISTING_APP.name)
    assertThat(viewModel.state.value.renaming).isTrue()
  }

  @Test
  fun `a blank name can't be submitted`() = runTest(testDispatcher) {
    val viewModel = createViewModel(appId = NEW_APP_ID)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TotpNameEntryEvent.NameChanged("   "))

    assertThat(viewModel.state.value.canSubmit).isFalse()

    viewModel.onEvent(TotpNameEntryEvent.NextClicked)

    assertThat(actions).isEmpty()
  }

  @Test
  fun `NextClicked names the newly confirmed app and goes back to the list`() = runTest(testDispatcher) {
    val viewModel = createViewModel(appId = NEW_APP_ID)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TotpNameEntryEvent.NameChanged("  Bitwarden Authenticator  "))
    viewModel.onEvent(TotpNameEntryEvent.NextClicked)

    coVerify { repository.nameNewTotpApp(NEW_APP_ID, "Bitwarden Authenticator") }
    assertThat(actions).contains(TotpNameEntryAction.ShowTotpAppSetUp)
    assertThat(actions.last()).isEqualTo(TotpNameEntryAction.NavigateToAccountSettings)
  }

  @Test
  fun `NextClicked renames an existing app and goes back to the list`() = runTest(testDispatcher) {
    val viewModel = createViewModel(appId = EXISTING_APP.id, renamedApp = EXISTING_APP)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TotpNameEntryEvent.NameChanged("Work Authenticator"))
    viewModel.onEvent(TotpNameEntryEvent.NextClicked)

    coVerify { repository.renameTotpApp(EXISTING_APP, "Work Authenticator") }
    assertThat(actions).contains(TotpNameEntryAction.ShowTotpAppRenamed)
    assertThat(actions.last()).isEqualTo(TotpNameEntryAction.NavigateToAccountSettings)
  }

  @Test
  fun `NavigateBackClicked leaves the screen`() = runTest(testDispatcher) {
    val viewModel = createViewModel(appId = NEW_APP_ID)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TotpNameEntryEvent.NavigateBackClicked)

    assertThat(actions.last()).isEqualTo(TotpNameEntryAction.NavigateBack)
  }

  @Test
  fun `entry is capped at the grapheme limit rather than rejected`() = runTest(testDispatcher) {
    val viewModel = createViewModel(appId = NEW_APP_ID)

    viewModel.onEvent(TotpNameEntryEvent.NameChanged("a".repeat(TotpRepository.MAX_NAME_LENGTH_GRAPHEMES + 20)))

    assertThat(viewModel.state.value.name).isEqualTo("a".repeat(TotpRepository.MAX_NAME_LENGTH_GRAPHEMES))
    assertThat(viewModel.state.value.canSubmit).isTrue()
  }

  /**
   * The case the byte trim exists for: thirty emoji are inside the grapheme cap and well past the 98 bytes the service
   * leaves room for, so the grapheme cap alone would let an unencryptable name through.
   */
  @Test
  fun `entry is also capped in bytes, which the grapheme limit does not guarantee`() = runTest(testDispatcher) {
    val viewModel = createViewModel(appId = NEW_APP_ID)

    viewModel.onEvent(TotpNameEntryEvent.NameChanged("\uD83D\uDD10".repeat(TotpRepository.MAX_NAME_LENGTH_GRAPHEMES)))

    val name = viewModel.state.value.name
    assertThat(name.toByteArray(Charsets.UTF_8).size <= TotpRepository.MAX_NAME_LENGTH_BYTES).isTrue()
    assertThat(name.isNotEmpty()).isTrue()
  }

  /** Trimming to a byte budget must not leave half a character behind. */
  @Test
  fun `capping in bytes does not split a character`() = runTest(testDispatcher) {
    val viewModel = createViewModel(appId = NEW_APP_ID)

    viewModel.onEvent(TotpNameEntryEvent.NameChanged("\uD83D\uDD10".repeat(TotpRepository.MAX_NAME_LENGTH_GRAPHEMES)))

    val name = viewModel.state.value.name
    assertThat(name.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)).isEqualTo(name)
    assertThat(name.length % 2).isEqualTo(0)
  }

  @Test
  fun `a name that didn't save leaves the user on the screen to try again`() = runTest(testDispatcher) {
    coEvery { repository.nameNewTotpApp(any(), any()) } returns TotpRepository.UpdateResult.NetworkFailure

    val viewModel = createViewModel(appId = NEW_APP_ID)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TotpNameEntryEvent.NameChanged("Aegis"))
    viewModel.onEvent(TotpNameEntryEvent.NextClicked)

    assertThat(actions.last()).isEqualTo(TotpNameEntryAction.ShowNameNotSaved)
    assertThat(viewModel.state.value.submitting).isFalse()
  }

  private fun createViewModel(appId: Long, renamedApp: TotpApp? = null) = TotpNameEntryViewModel(appId = appId, renamedApp = renamedApp, repository = repository)

  private fun TestScope.collectActions(actions: Flow<TotpNameEntryAction>): List<TotpNameEntryAction> {
    val collected = mutableListOf<TotpNameEntryAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
