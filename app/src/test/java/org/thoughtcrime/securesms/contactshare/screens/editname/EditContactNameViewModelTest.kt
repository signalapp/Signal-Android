/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contactshare.screens.editname

import androidx.lifecycle.SavedStateHandle
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class EditContactNameViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private val paigeHall = ContactNameParts(givenName = "Paige", familyName = "Hall")

  @Test
  fun `canSave is false before anything is edited`() = runTest(testDispatcher) {
    val viewModel = createViewModel(paigeHall)

    assertThat(viewModel.state.value.canSave).isFalse()
  }

  @Test
  fun `canSave is true once a name part changes`() = runTest(testDispatcher) {
    val viewModel = createViewModel(paigeHall)

    viewModel.onEvent(EditContactNameEvent.FamilyNameChanged("Halle"))

    assertThat(viewModel.state.value.canSave).isTrue()
  }

  @Test
  fun `canSave is false when every name part is cleared`() = runTest(testDispatcher) {
    val viewModel = createViewModel(paigeHall)

    viewModel.onEvent(EditContactNameEvent.GivenNameChanged(""))
    viewModel.onEvent(EditContactNameEvent.FamilyNameChanged(""))

    assertThat(viewModel.state.value.canSave).isFalse()
  }

  @Test
  fun `a prefix alone is not a saveable name`() = runTest(testDispatcher) {
    val viewModel = createViewModel(ContactNameParts())

    viewModel.onEvent(EditContactNameEvent.PrefixChanged("Dr."))

    assertThat(viewModel.state.value.canSave).isFalse()
  }

  @Test
  fun `a business carrying only a company is not treated as nameless`() {
    // The company is not editable here, but it still has to count as a name, or a business entry
    // could neither be saved nor sent.
    assertThat(ContactNameParts(organization = "Signal Messenger").hasDisplayableName).isTrue()
  }

  @Test
  fun `saving emits the edited parts`() = runTest(testDispatcher) {
    val viewModel = createViewModel(paigeHall)
    val results = viewModel.collectResults(this)

    viewModel.onEvent(EditContactNameEvent.GivenNameChanged("Paige"))
    viewModel.onEvent(EditContactNameEvent.FamilyNameChanged(""))
    viewModel.onEvent(EditContactNameEvent.SaveClicked)

    assertThat(results.single()).isEqualTo(EditContactNameResult.Saved(ContactNameParts(givenName = "Paige")))
  }

  @Test
  fun `saving an unchanged name emits nothing`() = runTest(testDispatcher) {
    val viewModel = createViewModel(paigeHall)
    val results = viewModel.collectResults(this)

    viewModel.onEvent(EditContactNameEvent.SaveClicked)

    assertThat(results).isEqualTo(emptyList<EditContactNameResult>())
  }

  @Test
  fun `backing out emits cancelled`() = runTest(testDispatcher) {
    val viewModel = createViewModel(paigeHall)
    val results = viewModel.collectResults(this)

    viewModel.onEvent(EditContactNameEvent.BackClicked)

    assertThat(results.single()).isEqualTo(EditContactNameResult.Cancelled)
  }

  private fun EditContactNameViewModel.collectResults(scope: TestScope): List<EditContactNameResult> {
    val collected = mutableListOf<EditContactNameResult>()
    scope.backgroundScope.launch { results.collect { collected += it } }
    return collected
  }

  private fun createViewModel(parts: ContactNameParts): EditContactNameViewModel {
    return EditContactNameViewModel(SavedStateHandle()).apply { onEvent(EditContactNameEvent.Initialize(parts)) }
  }
}
