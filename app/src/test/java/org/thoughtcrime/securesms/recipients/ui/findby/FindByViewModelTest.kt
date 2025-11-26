/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.findby

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository
import org.thoughtcrime.securesms.recipients.LiveRecipientCache
import org.thoughtcrime.securesms.recipients.PhoneNumber
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientRepository
import org.thoughtcrime.securesms.registration.ui.countrycode.Country
import org.thoughtcrime.securesms.registration.ui.countrycode.CountryUtils
import org.whispersystems.signalservice.api.push.ServiceId
import java.util.Optional

class FindByViewModelTest {

  private val liveRecipientCache = mockk<LiveRecipientCache>(relaxed = true)
  private lateinit var viewModel: FindByViewModel

  @Before
  fun setup() {
    mockkStatic(AppDependencies::class)
    every { AppDependencies.recipientCache } returns liveRecipientCache
    every { Recipient.self() } returns mockk {
      every { e164 } returns Optional.of("+15551234")
    }
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `Given phone number mode, when I change user entry, then I expect digits only`() {
    viewModel = FindByViewModel(FindByMode.PHONE_NUMBER)

    viewModel.onUserEntryChanged("123abc456")
    val result = viewModel.state.value.userEntry

    assertEquals("123456", result)
  }

  @Test
  fun `Given username mode, when I change user entry, then I expect unaltered value`() {
    viewModel = FindByViewModel(FindByMode.USERNAME)

    viewModel.onUserEntryChanged("username123")
    val result = viewModel.state.value.userEntry

    assertEquals("username123", result)
  }

  @Test
  fun `Given a selected country, when I update it, then I expect the state to reflect it`() {
    viewModel = FindByViewModel(FindByMode.PHONE_NUMBER)
    val country = Country(emoji = "", name = "United States", countryCode = 1, regionCode = "US")

    viewModel.onCountrySelected(country)
    val result = viewModel.state.value.selectedCountry

    assertEquals(country, result)
  }

  @Test
  fun `Given invalid username, when I click next, then I expect InvalidEntry`() = runTest {
    viewModel = FindByViewModel(FindByMode.USERNAME)

    viewModel.onUserEntryChanged("invalid username")
    val result = viewModel.onNextClicked()

    assertTrue(result is FindByResult.InvalidEntry)
  }

  @Test
  fun `Given empty phone number, when I click next, then I expect InvalidEntry`() = runTest {
    viewModel = FindByViewModel(FindByMode.PHONE_NUMBER)

    viewModel.onUserEntryChanged("")
    mockkStatic(RecipientRepository::class).apply {
      every { RecipientRepository.lookupNewE164(any()) } returns RecipientRepository.PhoneLookupResult.InvalidPhone(invalidValue = "")
    }

    val result = viewModel.onNextClicked()

    assertTrue(result is FindByResult.InvalidEntry)

    unmockkObject(RecipientRepository)
  }

  @Test
  fun `Given valid phone lookup, when I click next, then I expect Success`() = runTest {
    val recipient = mockk<Recipient> {
      every { id } returns RecipientId.from(123L)
    }

    mockkStatic(RecipientRepository::class).apply {
      every { RecipientRepository.lookupNewE164("+15551234") } returns
        RecipientRepository.PhoneLookupResult.Found(recipient, PhoneNumber("+15551234"))
    }

    viewModel = FindByViewModel(FindByMode.PHONE_NUMBER)
    val country = Country(emoji = "🇺🇸", name = "United States", countryCode = 1, regionCode = "US")

    viewModel.onCountrySelected(country)
    viewModel.onUserEntryChanged("5551234")

    val result = viewModel.onNextClicked()

    assertTrue(result is FindByResult.Success)
    assertEquals((result as FindByResult.Success).recipientId, recipient.id)

    unmockkObject(RecipientRepository)
  }

  @Test
  fun `Given unknown phone lookup, when I click next, then I expect NotFound`() = runTest {
    mockkStatic(RecipientRepository::class).apply {
      every { RecipientRepository.lookupNewE164(any()) } returns RecipientRepository.PhoneLookupResult.NotFound(PhoneNumber("0000000000"))
    }

    viewModel = FindByViewModel(FindByMode.PHONE_NUMBER)
    viewModel.onUserEntryChanged("0000000000")

    val result = viewModel.onNextClicked()

    assertTrue(result is FindByResult.NotFound)
    assertEquals(RecipientId.from(-1L), (result as FindByResult.NotFound).recipientId)

    unmockkObject(RecipientRepository)
  }

  @Test
  fun `Given matching country name, when I filter countries, then I expect matched countries`() {
    val countries = listOf(
      Country(emoji = "🇺🇸", name = "United States", countryCode = 1, regionCode = "US"),
      Country(emoji = "🇨🇦", name = "Canada", countryCode = 1, regionCode = "CA"),
      Country(emoji = "🇬🇧", name = "United Kingdom", countryCode = 44, regionCode = "GB")
    )

    mockkObject(CountryUtils).apply {
      every { CountryUtils.getCountries() } returns countries
    }

    viewModel = FindByViewModel(FindByMode.PHONE_NUMBER)

    viewModel.filterCountries("United")
    val result = viewModel.state.value.filteredCountries

    assertEquals(2, result.size)
    assertTrue(result.any { it.name == "United States" })
    assertTrue(result.any { it.name == "United Kingdom" })

    unmockkObject(CountryUtils)
  }

  @Test
  fun `Given matching country code, when I filter countries, then I expect matched countries`() {
    val countries = listOf(
      Country(emoji = "🇺🇸", name = "United States", countryCode = 1, regionCode = "US"),
      Country(emoji = "🇨🇦", name = "Canada", countryCode = 1, regionCode = "CA"),
      Country(emoji = "🇬🇧", name = "United Kingdom", countryCode = 44, regionCode = "GB")
    )

    mockkObject(CountryUtils).apply {
      every { CountryUtils.getCountries() } returns countries
    }

    viewModel = FindByViewModel(FindByMode.PHONE_NUMBER)

    viewModel.filterCountries("1")
    val result = viewModel.state.value.filteredCountries

    assertEquals(2, result.size)
    assertTrue(result.any { it.name == "United States" })
    assertTrue(result.any { it.name == "Canada" })

    unmockkObject(CountryUtils)
  }

  @Test
  fun `Given empty country filter, when I filter countries, then I expect empty countries list`() {
    val countries = listOf(
      Country(emoji = "🇺🇸", name = "United States", countryCode = 1, regionCode = "US"),
      Country(emoji = "🇨🇦", name = "Canada", countryCode = 1, regionCode = "CA"),
      Country(emoji = "🇬🇧", name = "United Kingdom", countryCode = 44, regionCode = "GB")
    )

    mockkObject(CountryUtils).apply {
      every { CountryUtils.getCountries() } returns countries
    }

    viewModel = FindByViewModel(FindByMode.PHONE_NUMBER)

    viewModel.filterCountries("")
    val result = viewModel.state.value.filteredCountries

    assertEquals(0, result.size)

    unmockkObject(CountryUtils)
  }

  @Test
  fun `Given username not found, when I click next, then I expect NotFound`() = runTest {
    mockkStatic(UsernameRepository::class)
    every { UsernameRepository.fetchAciForUsername("john") } returns UsernameRepository.UsernameAciFetchResult.NotFound

    viewModel = FindByViewModel(FindByMode.USERNAME)
    viewModel.onUserEntryChanged("@john")

    val result = viewModel.onNextClicked()

    assertTrue(result is FindByResult.NotFound)

    unmockkObject(UsernameRepository)
  }

  @Test
  fun `Given username fetch network error, when I click next, then I expect NetworkError`() = runTest {
    mockkStatic(UsernameRepository::class)
    every { UsernameRepository.fetchAciForUsername("jane") } returns UsernameRepository.UsernameAciFetchResult.NetworkError

    viewModel = FindByViewModel(FindByMode.USERNAME)
    viewModel.onUserEntryChanged("@jane")

    val result = viewModel.onNextClicked()

    assertTrue(result is FindByResult.NetworkError)

    unmockkObject(UsernameRepository)
  }

  @Test
  fun `Given valid username, when I click next, then I expect Success`() = runTest {
    val aci: ServiceId.ACI = mockk(relaxed = true)
    val username = "@doe"
    val recipientId = RecipientId.from(456L)

    mockkStatic(UsernameRepository::class).apply {
      every {
        UsernameRepository.fetchAciForUsername("doe") // stripped @
      } returns UsernameRepository.UsernameAciFetchResult.Success(aci)
    }

    mockkObject(Recipient)
    val mockRecipient = mockk<Recipient>()
    every { mockRecipient.id } returns recipientId
    every { Recipient.externalUsername(aci, username) } returns mockRecipient

    viewModel = FindByViewModel(FindByMode.USERNAME)
    viewModel.onUserEntryChanged(username)

    val result = viewModel.onNextClicked()

    assertTrue(result is FindByResult.Success)
    assertEquals(recipientId, (result as FindByResult.Success).recipientId)

    unmockkStatic(UsernameRepository::class)
    unmockkObject(Recipient)
  }
}
