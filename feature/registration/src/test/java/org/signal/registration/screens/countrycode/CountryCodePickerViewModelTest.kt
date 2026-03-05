/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.countrycode

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.core.ui.navigation.ResultEventBus
import org.signal.registration.RegistrationFlowEvent

@OptIn(ExperimentalCoroutinesApi::class)
class CountryCodePickerViewModelTest {

  private lateinit var mockRepository: CountryCodePickerRepository
  private lateinit var emittedEvents: MutableList<RegistrationFlowEvent>
  private lateinit var parentEventEmitter: (RegistrationFlowEvent) -> Unit
  private lateinit var resultBus: ResultEventBus
  private val resultKey = "test_country_result"

  private val testDispatcher = StandardTestDispatcher()

  private val testCountries = listOf(
    Country("\uD83C\uDDFA\uD83C\uDDF8", "United States", 1, "US"),
    Country("\uD83C\uDDE8\uD83C\uDDE6", "Canada", 1, "CA"),
    Country("\uD83C\uDDE9\uD83C\uDDEA", "Germany", 49, "DE"),
    Country("\uD83C\uDDEC\uD83C\uDDE7", "United Kingdom", 44, "GB"),
    Country("\uD83C\uDDEE\uD83C\uDDF3", "India", 91, "IN"),
    Country("\uD83C\uDDF3\uD83C\uDDF1", "Netherlands", 31, "NL"),
    Country("\uD83C\uDDFA\uD83C\uDDE6", "Ukraine", 380, "UA")
  )

  private val testCommonCountries = listOf(
    Country("\uD83C\uDDFA\uD83C\uDDF8", "United States", 1, "US"),
    Country("\uD83C\uDDE9\uD83C\uDDEA", "Germany", 49, "DE"),
    Country("\uD83C\uDDEE\uD83C\uDDF3", "India", 91, "IN"),
    Country("\uD83C\uDDF3\uD83C\uDDF1", "Netherlands", 31, "NL"),
    Country("\uD83C\uDDFA\uD83C\uDDE6", "Ukraine", 380, "UA")
  )

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    mockRepository = mockk(relaxed = true)
    emittedEvents = mutableListOf()
    parentEventEmitter = { event -> emittedEvents.add(event) }
    resultBus = ResultEventBus()

    coEvery { mockRepository.getCountries() } returns testCountries
    coEvery { mockRepository.getCommonCountries() } returns testCommonCountries
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(initialCountry: Country? = null): CountryCodePickerViewModel {
    return CountryCodePickerViewModel(mockRepository, parentEventEmitter, resultBus, resultKey, initialCountry)
  }

  // ==================== Initialization Tests ====================

  @Test
  fun `initial state has empty lists before loading completes`() {
    val state = CountryCodeState()

    assertThat(state.query).isEqualTo("")
    assertThat(state.countryList).isEqualTo(emptyList())
    assertThat(state.commonCountryList).isEqualTo(emptyList())
    assertThat(state.filteredList).isEqualTo(emptyList())
    assertThat(state.startingIndex).isEqualTo(0)
  }

  @Test
  fun `loadCountries populates country list and common country list`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    val state = viewModel.state.value
    assertThat(state.countryList).isNotEmpty()
    assertThat(state.commonCountryList).isNotEmpty()
  }

  @Test
  fun `loadCountries includes United States in country list`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    val state = viewModel.state.value
    val us = state.countryList.find { it.regionCode == "US" }
    assertThat(us != null).isTrue()
    assertThat(us!!.countryCode).isEqualTo(1)
  }

  @Test
  fun `loadCountries includes common countries`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    val state = viewModel.state.value
    val commonRegionCodes = state.commonCountryList.map { it.regionCode }
    assertThat(commonRegionCodes).contains("US")
    assertThat(commonRegionCodes).contains("DE")
    assertThat(commonRegionCodes).contains("IN")
  }

  @Test
  fun `loadCountries with initialCountry not in common list sets starting index`() = runTest {
    val canada = testCountries.find { it.regionCode == "CA" }!!
    val viewModel = createViewModel(initialCountry = canada)
    advanceUntilIdle()

    val state = viewModel.state.value
    assertThat(state.startingIndex).isGreaterThan(0)
  }

  @Test
  fun `loadCountries with common initialCountry keeps starting index at 0`() = runTest {
    val us = testCommonCountries.find { it.regionCode == "US" }!!
    val viewModel = createViewModel(initialCountry = us)
    advanceUntilIdle()

    val state = viewModel.state.value
    assertThat(state.startingIndex).isEqualTo(0)
  }

  @Test
  fun `loadCountries with null initialCountry keeps starting index at 0`() = runTest {
    val viewModel = createViewModel(initialCountry = null)
    advanceUntilIdle()

    assertThat(viewModel.state.value.startingIndex).isEqualTo(0)
  }

  // ==================== Search Tests ====================

  @Test
  fun `Search event filters countries by name`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.onEvent(CountryCodePickerScreenEvents.Search("United"))

    val state = viewModel.state.value
    assertThat(state.query).isEqualTo("United")
    assertThat(state.filteredList).isNotEmpty()
    assertThat(state.filteredList.all { it.name.contains("United", ignoreCase = true) }).isTrue()
  }

  @Test
  fun `Search event filters countries by country code`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.onEvent(CountryCodePickerScreenEvents.Search("49"))

    val state = viewModel.state.value
    assertThat(state.filteredList).isNotEmpty()
    assertThat(state.filteredList.any { it.countryCode == 49 }).isTrue()
  }

  @Test
  fun `Search event filters countries by country code with plus prefix`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.onEvent(CountryCodePickerScreenEvents.Search("+49"))

    val state = viewModel.state.value
    assertThat(state.filteredList).isNotEmpty()
    assertThat(state.filteredList.any { it.countryCode == 49 }).isTrue()
  }

  @Test
  fun `Search event is case insensitive`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.onEvent(CountryCodePickerScreenEvents.Search("germany"))

    val state = viewModel.state.value
    assertThat(state.filteredList).isNotEmpty()
    assertThat(state.filteredList.any { it.regionCode == "DE" }).isTrue()
  }

  @Test
  fun `Search for USA matches United States`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.onEvent(CountryCodePickerScreenEvents.Search("usa"))

    val state = viewModel.state.value
    assertThat(state.filteredList).isNotEmpty()
    assertThat(state.filteredList.any { it.regionCode == "US" }).isTrue()
  }

  @Test
  fun `Empty search clears filtered list`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    // First, search for something
    viewModel.onEvent(CountryCodePickerScreenEvents.Search("United"))
    assertThat(viewModel.state.value.filteredList).isNotEmpty()

    // Then clear it
    viewModel.onEvent(CountryCodePickerScreenEvents.Search(""))

    val state = viewModel.state.value
    assertThat(state.query).isEqualTo("")
    assertThat(state.filteredList).isEqualTo(emptyList())
  }

  @Test
  fun `Search with no matches returns empty filtered list`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.onEvent(CountryCodePickerScreenEvents.Search("xyznonexistent"))

    val state = viewModel.state.value
    assertThat(state.filteredList).isEqualTo(emptyList())
  }

  // ==================== CountrySelected Tests ====================

  @Test
  fun `CountrySelected sends result via result bus and navigates back`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    val country = Country("\uD83C\uDDFA\uD83C\uDDF8", "United States", 1, "US")
    viewModel.onEvent(CountryCodePickerScreenEvents.CountrySelected(country))

    val result = resultBus.channelMap[resultKey]?.tryReceive()?.getOrNull()
    assertThat(result).isEqualTo(country)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }

  @Test
  fun `Dismissed does not send result to result bus`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.onEvent(CountryCodePickerScreenEvents.Dismissed)

    val result = resultBus.channelMap[resultKey]?.tryReceive()?.getOrNull()
    assertThat(result).isNull()
  }

  // ==================== Dismissed Tests ====================

  @Test
  fun `Dismissed navigates back`() = runTest {
    val viewModel = createViewModel()
    advanceUntilIdle()

    viewModel.onEvent(CountryCodePickerScreenEvents.Dismissed)

    assertThat(emittedEvents).hasSize(1)
    assertThat(emittedEvents.first()).isEqualTo(RegistrationFlowEvent.NavigateBack)
  }
}
