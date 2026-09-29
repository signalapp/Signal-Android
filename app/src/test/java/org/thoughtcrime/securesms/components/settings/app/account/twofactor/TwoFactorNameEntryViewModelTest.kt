/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.account.twofactor

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.signal.appsettings.account.TwoFactorMethod
import org.signal.appsettings.twofactornameentry.TwoFactorNameEntryAction
import org.signal.appsettings.twofactornameentry.TwoFactorNameEntryEvent
import org.signal.core.models.MasterKey
import org.signal.libsignal.net.MfaKeyNotFoundException
import org.signal.libsignal.net.RequestResult
import org.signal.network.service.TwoFactorMethodService
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TwoFactorNameEntryViewModelTest {

  companion object {
    private val EXISTING_APP = TwoFactorMethod(id = 7, kind = TwoFactorMethod.Kind.AUTHENTICATOR_APP, name = "Twilio Authy", createdAt = 0)
    private val EXISTING_PASSKEY = TwoFactorMethod(id = 8, kind = TwoFactorMethod.Kind.PASSKEY, name = "Pixel Phone", createdAt = 0)

    /** The id the service assigned when the method was added, before it had a name. */
    private const val NEW_METHOD_ID = 1L
    private const val NEW_METHOD_CREATED_AT = 1000L

    /** Distinct from any stored date, so a rename that restamps can't pass for one that preserves. */
    private const val NOW = 9000L

    private val MASTER_KEY = MasterKey(ByteArray(32) { (it + 100).toByte() })
  }

  private val testDispatcher = UnconfinedTestDispatcher()
  private val service: TwoFactorMethodService = mockk(relaxed = true)

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)

    coEvery { service.setName(any(), any(), any(), any()) } returns RequestResult.Success(Unit)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `naming a new method starts empty and isn't renaming`() = runTest(testDispatcher) {
    val viewModel = createViewModel(methodId = NEW_METHOD_ID)

    assertThat(viewModel.state.value.name).isEqualTo("")
    assertThat(viewModel.state.value.renaming).isFalse()
  }

  @Test
  fun `renaming starts from the method's current name`() = runTest(testDispatcher) {
    val viewModel = createViewModel(methodId = EXISTING_APP.id, renamedMethod = EXISTING_APP)

    assertThat(viewModel.state.value.name).isEqualTo(EXISTING_APP.name)
    assertThat(viewModel.state.value.renaming).isTrue()
  }

  @Test
  fun `a blank name can't be submitted`() = runTest(testDispatcher) {
    val viewModel = createViewModel(methodId = NEW_METHOD_ID)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TwoFactorNameEntryEvent.NameChanged("   "))

    assertThat(viewModel.state.value.canSubmit).isFalse()

    viewModel.onEvent(TwoFactorNameEntryEvent.NextClicked)

    assertThat(actions).isEmpty()
  }

  @Test
  fun `NextClicked names the newly confirmed app and goes back to the list`() = runTest(testDispatcher) {
    val viewModel = createViewModel(methodId = NEW_METHOD_ID)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TwoFactorNameEntryEvent.NameChanged("  Bitwarden Authenticator  "))
    viewModel.onEvent(TwoFactorNameEntryEvent.NextClicked)

    coVerify { service.setName(NEW_METHOD_ID, "Bitwarden Authenticator", Instant.ofEpochMilli(NEW_METHOD_CREATED_AT), MASTER_KEY) }
    assertThat(actions).contains(TwoFactorNameEntryAction.ShowMethodSetUp(TwoFactorMethod.Kind.AUTHENTICATOR_APP))
    assertThat(actions.last()).isEqualTo(TwoFactorNameEntryAction.NavigateToAccountSettings)
  }

  @Test
  fun `NextClicked renames an existing app and goes back to the list`() = runTest(testDispatcher) {
    val viewModel = createViewModel(methodId = EXISTING_APP.id, renamedMethod = EXISTING_APP)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TwoFactorNameEntryEvent.NameChanged("Work Authenticator"))
    viewModel.onEvent(TwoFactorNameEntryEvent.NextClicked)

    coVerify { service.setName(EXISTING_APP.id, "Work Authenticator", Instant.ofEpochMilli(EXISTING_APP.createdAt!!), MASTER_KEY) }
    assertThat(actions).contains(TwoFactorNameEntryAction.ShowMethodRenamed(TwoFactorMethod.Kind.AUTHENTICATOR_APP))
    assertThat(actions.last()).isEqualTo(TwoFactorNameEntryAction.NavigateToAccountSettings)
  }

  @Test
  fun `renaming a passkey reports the passkey toast`() = runTest(testDispatcher) {
    val viewModel = createViewModel(methodId = EXISTING_PASSKEY.id, renamedMethod = EXISTING_PASSKEY)
    val actions = collectActions(viewModel.actions)

    assertThat(viewModel.state.value.kind).isEqualTo(TwoFactorMethod.Kind.PASSKEY)

    viewModel.onEvent(TwoFactorNameEntryEvent.NameChanged("Work Phone"))
    viewModel.onEvent(TwoFactorNameEntryEvent.NextClicked)

    coVerify { service.setName(EXISTING_PASSKEY.id, "Work Phone", Instant.ofEpochMilli(EXISTING_PASSKEY.createdAt!!), MASTER_KEY) }
    assertThat(actions).contains(TwoFactorNameEntryAction.ShowMethodRenamed(TwoFactorMethod.Kind.PASSKEY))
  }

  @Test
  fun `NavigateBackClicked leaves the screen`() = runTest(testDispatcher) {
    val viewModel = createViewModel(methodId = NEW_METHOD_ID)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TwoFactorNameEntryEvent.NavigateBackClicked)

    assertThat(actions.last()).isEqualTo(TwoFactorNameEntryAction.NavigateBack)
  }

  @Test
  fun `entry is capped at the grapheme limit rather than rejected`() = runTest(testDispatcher) {
    val viewModel = createViewModel(methodId = NEW_METHOD_ID)

    viewModel.onEvent(TwoFactorNameEntryEvent.NameChanged("a".repeat(TwoFactorNameEntryViewModel.MAX_NAME_LENGTH_GRAPHEMES + 20)))

    assertThat(viewModel.state.value.name).isEqualTo("a".repeat(TwoFactorNameEntryViewModel.MAX_NAME_LENGTH_GRAPHEMES))
    assertThat(viewModel.state.value.canSubmit).isTrue()
  }

  /**
   * The case the byte trim exists for: thirty emoji are inside the grapheme cap and well past the 98 bytes the service
   * leaves room for, so the grapheme cap alone would let an unencryptable name through.
   */
  @Test
  fun `entry is also capped in bytes, which the grapheme limit does not guarantee`() = runTest(testDispatcher) {
    val viewModel = createViewModel(methodId = NEW_METHOD_ID)

    viewModel.onEvent(TwoFactorNameEntryEvent.NameChanged("\uD83D\uDD10".repeat(TwoFactorNameEntryViewModel.MAX_NAME_LENGTH_GRAPHEMES)))

    val name = viewModel.state.value.name
    assertThat(name.toByteArray(Charsets.UTF_8).size).isLessThanOrEqualTo(TwoFactorMethodService.MAX_NAME_LENGTH_BYTES)
    assertThat(name).isNotEmpty()
  }

  /** Trimming to a byte budget must not leave half a character behind. */
  @Test
  fun `capping in bytes does not split a character`() = runTest(testDispatcher) {
    val viewModel = createViewModel(methodId = NEW_METHOD_ID)

    viewModel.onEvent(TwoFactorNameEntryEvent.NameChanged("\uD83D\uDD10".repeat(TwoFactorNameEntryViewModel.MAX_NAME_LENGTH_GRAPHEMES)))

    val name = viewModel.state.value.name
    assertThat(name.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)).isEqualTo(name)
    assertThat(name.codePointCount(0, name.length)).isEqualTo(name.length / 2)
  }

  @Test
  fun `a name that didn't save leaves the user on the screen to try again`() = runTest(testDispatcher) {
    coEvery { service.setName(any(), any(), any(), any()) } returns RequestResult.RetryableNetworkError(IOException("offline"))

    val viewModel = createViewModel(methodId = NEW_METHOD_ID)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TwoFactorNameEntryEvent.NameChanged("Aegis"))
    viewModel.onEvent(TwoFactorNameEntryEvent.NextClicked)

    assertThat(actions.last()).isEqualTo(TwoFactorNameEntryAction.ShowNameNotSaved)
    assertThat(viewModel.state.value.submitting).isFalse()
  }

  /** A method whose metadata we couldn't read has no date to preserve, so it gets stamped with the current time. */
  @Test
  fun `renaming a method with no readable date stamps it with the current time`() = runTest(testDispatcher) {
    val unreadable = TwoFactorMethod(id = 11, kind = TwoFactorMethod.Kind.PASSKEY, name = null, createdAt = null)
    val viewModel = createViewModel(methodId = unreadable.id, renamedMethod = unreadable)

    viewModel.onEvent(TwoFactorNameEntryEvent.NameChanged("Pixel Phone"))
    viewModel.onEvent(TwoFactorNameEntryEvent.NextClicked)

    coVerify { service.setName(unreadable.id, "Pixel Phone", Instant.ofEpochMilli(NOW), MASTER_KEY) }
  }

  /** The method is already gone, so there is nothing to name and nothing the user can do about it here. */
  @Test
  fun `naming a method the service no longer has goes back to the list`() = runTest(testDispatcher) {
    coEvery { service.setName(any(), any(), any(), any()) } returns RequestResult.NonSuccess(MfaKeyNotFoundException("gone"))

    val viewModel = createViewModel(methodId = NEW_METHOD_ID)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TwoFactorNameEntryEvent.NameChanged("Aegis"))
    viewModel.onEvent(TwoFactorNameEntryEvent.NextClicked)

    assertThat(actions).isEqualTo(listOf(TwoFactorNameEntryAction.NavigateToAccountSettings))
  }

  private fun createViewModel(methodId: Long?, renamedMethod: TwoFactorMethod? = null) = TwoFactorNameEntryViewModel(
    methodId = methodId,
    kind = renamedMethod?.kind ?: TwoFactorMethod.Kind.AUTHENTICATOR_APP,
    createdAt = NEW_METHOD_CREATED_AT,
    renamedMethod = renamedMethod,
    service = service,
    masterKeyProvider = { MASTER_KEY },
    clock = { NOW }
  )

  /** Nothing should reach this screen without an id, but a bad argument must not become a request against key -1. */
  @Test
  fun `naming a method we were not told the id of gives up rather than naming the wrong one`() = runTest(testDispatcher) {
    val viewModel = createViewModel(methodId = null)
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(TwoFactorNameEntryEvent.NameChanged("Aegis"))
    viewModel.onEvent(TwoFactorNameEntryEvent.NextClicked)

    assertThat(actions.last()).isEqualTo(TwoFactorNameEntryAction.NavigateToAccountSettings)
    coVerify(exactly = 0) { service.setName(any(), any(), any(), any()) }
  }

  private fun TestScope.collectActions(actions: Flow<TwoFactorNameEntryAction>): List<TwoFactorNameEntryAction> {
    val collected = mutableListOf<TwoFactorNameEntryAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
