package org.thoughtcrime.securesms.components.settings.app.subscription.boost

import android.app.Application
import android.text.SpannableStringBuilder
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNull
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

    assertEquals("5", result)
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
}
