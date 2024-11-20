package org.thoughtcrime.securesms.components.settings.app.subscription.boost

import android.app.Application
import android.text.SpannableStringBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Currency
import java.util.Locale

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class BoostTest__MoneyFilter {

  private val usd = Currency.getInstance("USD")
  private val yen = Currency.getInstance("JPY")
  private val inr = Currency.getInstance("INR")

  @Before
  fun setUp() {
    Locale.setDefault(Locale.US)
  }

  @Test
  fun `Given USD, when I enter 5, then I expect $ 5`() {
    val testSubject = Boost.MoneyFilter(usd)
    val editable = SpannableStringBuilder("5")

    testSubject.afterTextChanged(editable)

    assertEquals("$5", editable.toString())
  }

  @Test
  fun `Given USD, when I enter 5dot00, then I expect successful filter`() {
    val testSubject = Boost.MoneyFilter(usd)
    val editable = SpannableStringBuilder("5.00")
    val dest = SpannableStringBuilder()

    testSubject.afterTextChanged(editable)
    val filterResult = testSubject.filter(editable, 0, editable.length, dest, 0, 0)

    assertNull(filterResult)
  }

  @Test
  fun `Given USD, when I enter 5dot00, then I expect 5 from text change`() {
    var result = ""
    val testSubject = Boost.MoneyFilter(usd) {
      result = it
    }

    val editable = SpannableStringBuilder("5.00")
    testSubject.afterTextChanged(editable)

    assertEquals("5.00", result)
  }

  @Test
  fun `Given USD, when I enter 00005dot00, then I expect 5 from text change`() {
    val testSubject = Boost.MoneyFilter(usd)
    val editable = SpannableStringBuilder("00005.00")

    testSubject.afterTextChanged(editable)

    assertEquals("$5.00", editable.toString())
  }

  @Test
  fun `Given USD, when I enter 5dot000, then I expect successful filter`() {
    val testSubject = Boost.MoneyFilter(yen)
    val editable = SpannableStringBuilder("5.000")
    val dest = SpannableStringBuilder()

    testSubject.afterTextChanged(editable)
    val filterResult = testSubject.filter(editable, 0, editable.length, dest, 0, 0)

    assertNull(filterResult)
  }

  @Test
  fun `Given USD, when I enter 5dot, then I expect successful filter`() {
    val testSubject = Boost.MoneyFilter(usd)
    val editable = SpannableStringBuilder("5.")
    val dest = SpannableStringBuilder()

    testSubject.afterTextChanged(editable)
    val filterResult = testSubject.filter(editable, 0, editable.length, dest, 0, 0)

    assertNull(filterResult)
  }

  @Test
  fun `Given JPY, when I enter 5, then I expect yen 5`() {
    val testSubject = Boost.MoneyFilter(yen)
    val editable = SpannableStringBuilder("5")

    testSubject.afterTextChanged(editable)

    assertEquals("¥5", editable.toString())
  }

  @Test
  fun `Given JPY, when I enter 5, then I expect 5 from text change`() {
    var result = ""
    val testSubject = Boost.MoneyFilter(yen) {
      result = it
    }

    val editable = SpannableStringBuilder("5")

    testSubject.afterTextChanged(editable)

    assertEquals("5", result)
  }

  @Test
  fun `Given JPY, when I enter 5, then I expect successful filter`() {
    val testSubject = Boost.MoneyFilter(yen)
    val editable = SpannableStringBuilder("5")
    val dest = SpannableStringBuilder()

    testSubject.afterTextChanged(editable)
    val filterResult = testSubject.filter(editable, 0, editable.length, dest, 0, 0)

    assertNull(filterResult)
  }

  @Test
  fun `Given JPY, when I enter 5dot, then I expect unsuccessful filter`() {
    val testSubject = Boost.MoneyFilter(yen)
    val editable = SpannableStringBuilder("¥5.")
    val dest = SpannableStringBuilder()

    testSubject.afterTextChanged(editable)
    val filterResult = testSubject.filter(editable, 0, editable.length, dest, 0, 0)

    assertNotNull(filterResult)
  }

  @Test
  fun `Given MR and INR, when I enter 5dot55, then I expect localized`() {
    Locale.setDefault(Locale.forLanguageTag("mr"))

    val testSubject = Boost.MoneyFilter(inr)
    val editable = SpannableStringBuilder("5.55")

    testSubject.afterTextChanged(editable)

    assertEquals("₹५.५५", editable.toString())
  }

  @Test
  fun `Given MR and INR, when I enter dot, then I expect it to be retained in output`() {
    Locale.setDefault(Locale.forLanguageTag("mr"))

    val testSubject = Boost.MoneyFilter(inr)
    val editable = SpannableStringBuilder("₹५.")

    testSubject.afterTextChanged(editable)

    assertEquals("₹५.", editable.toString())
  }

  @Test
  fun `Given RTL indicator, when I enter five, then I expect successful match`() {
    val testSubject = Boost.MoneyFilter(yen)
    val editable = SpannableStringBuilder("\u200F5")
    val dest = SpannableStringBuilder()

    testSubject.afterTextChanged(editable)
    val filterResult = testSubject.filter(editable, 0, editable.length, dest, 0, 0)

    assertNull(filterResult)
  }

  @Test
  fun `Given USD, when I enter 1dot05, then I expect 1dot05`() {
    var result = ""
    val testSubject = Boost.MoneyFilter(usd) {
      result = it
    }

    val editable = SpannableStringBuilder("$1.05")
    testSubject.afterTextChanged(editable)

    assertEquals("1.05", result)
  }

  @Test
  fun `Given USD, when I enter 0dot05, then I expect 0dot05`() {
    var result = ""
    val testSubject = Boost.MoneyFilter(usd) {
      result = it
    }

    val editable = SpannableStringBuilder("$0.05")
    testSubject.afterTextChanged(editable)

    assertEquals("0.05", result)
  }

  @Test
  fun `Given USD, when I enter dot1, then I expect 0dot1`() {
    var result = ""
    val testSubject = Boost.MoneyFilter(usd) {
      result = it
    }

    val editable = SpannableStringBuilder("$.1")
    testSubject.afterTextChanged(editable)

    assertEquals("0.1", result)
  }

  @Test
  fun `Given USD, when I enter dot0, then I expect 0dot0`() {
    var result = ""
    val testSubject = Boost.MoneyFilter(usd) {
      result = it
    }

    val editable = SpannableStringBuilder(".0")
    testSubject.afterTextChanged(editable)

    assertEquals("0.0", result)
  }
}
