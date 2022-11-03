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
class CreditCardTextWatcherBackspaceTest(
  private val beforeBackspace: String,
  private val textWatcherOutput: String
) {

  private val testSubject = CreditCardTextWatcher()

  @Test
  fun getTextWatcherOutput() {
    val editable: Editable = SpannableStringBuilder(beforeBackspace.dropLast(1))
    testSubject.onTextChanged(null, 0, 0, 0)
    testSubject.afterTextChanged(editable)
    assertEquals(textWatcherOutput, editable.toString())
  }

  companion object {
    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters(name = "{index}: getTextWatcherOutput(..) = {0}, {1}")
    fun data(): Iterable<Array<Any>> = arrayListOf(
      arrayOf("1234 ", "123"),
      arrayOf("1234 5", "1234 ")
    )
  }
}
