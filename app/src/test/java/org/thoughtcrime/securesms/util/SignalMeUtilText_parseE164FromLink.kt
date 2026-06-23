package org.thoughtcrime.securesms.util

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.SignalMeUtil.parseE164FromLink

@RunWith(Parameterized::class)
class SignalMeUtilText_parseE164FromLink(private val input: String?, private val output: String?) {

  @Before
  fun setUp() {
    mockkObject(SignalStore)
    every { SignalStore.account.e164 } returns "+15555555555"
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun parse() {
    assertEquals(output, parseE164FromLink(input))
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun data(): Collection<Array<Any?>> {
      return listOf(
        arrayOf("https://signal.me/#p/+15555555555", "+15555555555"),
        arrayOf("https://signal.me/#p/5555555555", null),
        arrayOf("https://signal.me", null),
        arrayOf("https://signal.me/#p/", null),
        arrayOf("signal.me/#p/+15555555555", null),
        arrayOf("sgnl://signal.me/#p/+15555555555", "+15555555555"),
        arrayOf("sgnl://signal.me/#p/5555555555", null),
        arrayOf("sgnl://signal.me", null),
        arrayOf("sgnl://signal.me/#p/", null),
        arrayOf("", null),
        arrayOf(null, null)
      )
    }
  }
}
