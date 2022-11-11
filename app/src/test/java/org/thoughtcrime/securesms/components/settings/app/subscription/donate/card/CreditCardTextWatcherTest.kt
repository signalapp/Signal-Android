package org.thoughtcrime.securesms.components.settings.app.subscription.donate.card

import android.app.Application
import android.text.Editable
import android.text.SpannableStringBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = Application::class)
class CreditCardTextWatcherTest(
  private val userInput: String,
  private val textWatcherOutput: String
) {

  private val testSubject = CreditCardTextWatcher()

  @Test
  fun getTextWatcherOutput() {
    val editable: Editable = SpannableStringBuilder(userInput)
    testSubject.afterTextChanged(editable)
    assertEquals(textWatcherOutput, editable.toString())
  }

  companion object {
    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters(name = "{index}: getTextWatcherOutput(..) = {0}, {1}")
    fun data(): Iterable<Array<Any>> = arrayListOf(
      // AMEX
      arrayOf("340", "340"),
      arrayOf("3400", "3400 "),
      arrayOf("34000", "3400 0"),
      arrayOf("3400000000", "3400 000000 "),
      arrayOf("34000000000", "3400 000000 0"),
      arrayOf("340000000000000", "3400 000000 00000"),
      // UNIONPAY
      arrayOf("620", "620"),
      arrayOf("6200", "6200 "),
      arrayOf("62000", "6200 0"),
      arrayOf("6200000000", "6200 0000 00"),
      arrayOf("6200000000000", "6200 0000 00000"),
      arrayOf("620000000000000", "6200 0000 0000 000"),
      arrayOf("6200000000000000", "6200 0000 0000 0000"),
      arrayOf("62000000000000000", "62000 00000 00000 00"),
      // OTHER
      arrayOf("550", "550"),
      arrayOf("5500", "5500 "),
      arrayOf("55000", "5500 0"),
      arrayOf("5500000000", "5500 0000 00"),
      arrayOf("55000000000", "5500 0000 000"),
      arrayOf("550000000000000", "5500 0000 0000 000"),
      arrayOf("5500000000000000", "5500 0000 0000 0000"),
      arrayOf("55000000000000000", "55000 00000 00000 00"),
    )
  }
}
