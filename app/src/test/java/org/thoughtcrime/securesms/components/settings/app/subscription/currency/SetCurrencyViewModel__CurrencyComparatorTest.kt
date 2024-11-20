package org.thoughtcrime.securesms.components.settings.app.subscription.currency

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.Currency

@Suppress("ClassName")
@RunWith(JUnit4::class)
class SetCurrencyViewModel__CurrencyComparatorTest {

  private val currencyComparator = SetCurrencyViewModel.CurrencyComparator(listOf("AUD", "EUR", "CAD"))

  @Test
  fun givenAListOfCurrencies_whenISort_thenIExpectTheProperOrder() {
    // GIVEN
    val currencies = listOf("EUR", "AUD", "JPY", "USD", "CAD", "BWP", "BIF").map { Currency.getInstance(it) }
    val expected = listOf("USD", "AUD", "CAD", "EUR", "BWP", "BIF", "JPY").map { Currency.getInstance(it) }

    // WHEN
    val sorted: List<Currency> = currencies.sortedWith(currencyComparator)

    // THEN
    assertEquals(expected, sorted)
  }

  @Test
  fun givenUSDAndADefaultCurrency_whenISort_thenIExpectUSDFirst() {
    // GIVEN
    val currencies = listOf("EUR", "USD").map { Currency.getInstance(it) }
    val expected = listOf("USD", "EUR").map { Currency.getInstance(it) }

    // WHEN
    val sorted: List<Currency> = currencies.sortedWith(currencyComparator)

    // THEN
    assertEquals(expected, sorted)
  }

  @Test
  fun givenADefaultCurrencyAndANonDefaultCurrency_whenISort_thenIExpectUSDFirst() {
    // GIVEN
    val currencies = listOf("JPY", "EUR").map { Currency.getInstance(it) }
    val expected = listOf("EUR", "JPY").map { Currency.getInstance(it) }

    // WHEN
    val sorted: List<Currency> = currencies.sortedWith(currencyComparator)

    // THEN
    assertEquals(expected, sorted)
  }

  @Test
  fun givenTwoDefaultCurrencies_whenISort_thenIExpectOrderedByDisplayName() {
    // GIVEN
    val currencies = listOf("EUR", "AUD").map { Currency.getInstance(it) }
    val expected = listOf("AUD", "EUR").map { Currency.getInstance(it) }

    // WHEN
    val sorted = currencies.sortedWith(currencyComparator)

    // THEN
    assertEquals(expected, sorted)
  }

  @Test
  fun givenTwoNonDefaultCurrencies_whenISort_thenIExpectOrderedByDisplayName() {
    // GIVEN
    val currencies = listOf("XPF", "BIF").map { Currency.getInstance(it) }
    val expected = listOf("BIF", "XPF").map { Currency.getInstance(it) }

    // WHEN
    val sorted = currencies.sortedWith(currencyComparator)

    // THEN
    assertEquals(expected, sorted)
  }
}
