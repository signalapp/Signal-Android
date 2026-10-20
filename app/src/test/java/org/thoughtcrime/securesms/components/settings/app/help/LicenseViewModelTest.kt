/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.help

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
import org.signal.appsettings.help.LicenseAction
import org.signal.appsettings.help.LicenseEvent
import org.signal.appsettings.help.LicenseState
import org.thoughtcrime.securesms.testing.CoroutineDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class LicenseViewModelTest {

  companion object {
    private val LICENSE_LINES = listOf("Copyright Signal Messenger", "", "Licensed under the GNU AGPLv3")
  }

  private val testDispatcher = UnconfinedTestDispatcher()

  @get:Rule
  val dispatcherRule = CoroutineDispatcherRule(testDispatcher)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(LicenseRepository)

    coEvery { LicenseRepository.getLicenseLines() } returns LICENSE_LINES
  }

  @After
  fun tearDown() {
    unmockkObject(LicenseRepository)
    Dispatchers.resetMain()
  }

  @Test
  fun `the license text is read out of the repository on creation`() = runTest(testDispatcher) {
    val viewModel = LicenseViewModel()

    assertThat(viewModel.state.value).isEqualTo(LicenseState(licenseLines = LICENSE_LINES))
  }

  @Test
  fun `NavigateBackClicked navigates back`() = runTest(testDispatcher) {
    val viewModel = LicenseViewModel()
    val actions = collectActions(viewModel.actions)

    viewModel.onEvent(LicenseEvent.NavigateBackClicked)

    assertThat(actions).containsExactly(LicenseAction.NavigateBack)
  }

  private fun TestScope.collectActions(actions: Flow<LicenseAction>): List<LicenseAction> {
    val collected = mutableListOf<LicenseAction>()
    backgroundScope.launch { actions.toList(collected) }
    return collected
  }
}
