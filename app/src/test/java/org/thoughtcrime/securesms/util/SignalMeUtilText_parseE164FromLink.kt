package org.thoughtcrime.securesms.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import junit.framework.TestCase
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.dependencies.AppDependencies.application
import org.thoughtcrime.securesms.dependencies.MockApplicationDependencyProvider
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.SignalMeUtil.parseE164FromLink

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class SignalMeUtilText_parseE164FromLink(private val input: String?, private val output: String?) {

  @Before
  fun setUp() {
    if (!AppDependencies.isInitialized) {
      AppDependencies.init(ApplicationProvider.getApplicationContext(), MockApplicationDependencyProvider())
    }

    mockkObject(SignalStore)
    every { SignalStore.account.e164 } returns "+15555555555"
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun parse() {
    TestCase.assertEquals(output, parseE164FromLink(application, input))
  }

  companion object {
    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters
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
