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
class CreditCardExpirationTextWatcherTest(
  private val userInput: String,
  private val textWatcherOutput: String
) {

  private val testSubject = CreditCardExpirationTextWatcher()

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
      arrayOf("0", "0"),
      arrayOf("1", "1"),
      arrayOf("12", "12/"),
      arrayOf("02", "02/"),
      arrayOf("2", "02/"),
      arrayOf("12/", "12/"),
      arrayOf("12/1", "12/1"),
      arrayOf("15", "15")
    )
  }
}
