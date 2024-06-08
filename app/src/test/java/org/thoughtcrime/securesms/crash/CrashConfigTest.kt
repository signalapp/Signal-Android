package org.thoughtcrime.securesms.crash

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.assertIs
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.dependencies.MockApplicationDependencyProvider
import org.thoughtcrime.securesms.keyvalue.AccountValues
import org.thoughtcrime.securesms.keyvalue.KeyValueDataSet
import org.thoughtcrime.securesms.keyvalue.KeyValueStore
import org.thoughtcrime.securesms.keyvalue.MockKeyValuePersistentStorage
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.FeatureFlags
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class CrashConfigTest {

  @Before
  fun setup() {
    mockkObject(FeatureFlags)

    if (!AppDependencies.isInitialized) {
      AppDependencies.init(ApplicationProvider.getApplicationContext(), MockApplicationDependencyProvider())
    }

    val store = KeyValueStore(
      MockKeyValuePersistentStorage.withDataSet(
        KeyValueDataSet().apply {
          putString(AccountValues.KEY_ACI, UUID.randomUUID().toString())
        }
      )
    )

    SignalStore.inject(store)
  }

  @Test
  fun `simple name pattern`() {
    every { FeatureFlags.crashPromptConfig() } returns """[ { "name": "test", "percent": 100 } ]"""
    CrashConfig.computePatterns() assertIs listOf(CrashConfig.CrashPattern(namePattern = "test"))
  }

  @Test
  fun `simple message pattern`() {
    every { FeatureFlags.crashPromptConfig() } returns """[ { "message": "test", "percent": 100 } ]"""
    CrashConfig.computePatterns() assertIs listOf(CrashConfig.CrashPattern(messagePattern = "test"))
  }

  @Test
  fun `simple stackTrace pattern`() {
    every { FeatureFlags.crashPromptConfig() } returns """[ { "stackTrace": "test", "percent": 100 } ]"""
    CrashConfig.computePatterns() assertIs listOf(CrashConfig.CrashPattern(stackTracePattern = "test"))
  }

  @Test
  fun `all fields set`() {
    every { FeatureFlags.crashPromptConfig() } returns """[ { "name": "test1", "message": "test2", "stackTrace": "test3", "percent": 100 } ]"""
    CrashConfig.computePatterns() assertIs listOf(CrashConfig.CrashPattern(namePattern = "test1", messagePattern = "test2", stackTracePattern = "test3"))
  }

  @Test
  fun `multiple configs`() {
    every { FeatureFlags.crashPromptConfig() } returns
      """
      [ 
        { "name": "test1", "percent": 100 },
        { "message": "test2", "percent": 100 },
        { "stackTrace": "test3", "percent": 100 }
      ]
      """

    CrashConfig.computePatterns() assertIs listOf(
      CrashConfig.CrashPattern(namePattern = "test1"),
      CrashConfig.CrashPattern(messagePattern = "test2"),
      CrashConfig.CrashPattern(stackTracePattern = "test3")
    )
  }

  @Test
  fun `empty fields are considered null`() {
    every { FeatureFlags.crashPromptConfig() } returns
      """
      [ 
        { "name": "", "percent": 100 },
        { "name": "test1", "message": "", "percent": 100 },
        { "message": "test2", "stackTrace": "", "percent": 100 }
      ]
      """

    CrashConfig.computePatterns() assertIs listOf(
      CrashConfig.CrashPattern(namePattern = "test1"),
      CrashConfig.CrashPattern(messagePattern = "test2")
    )
  }

  @Test
  fun `ignore zero percent`() {
    every { FeatureFlags.crashPromptConfig() } returns """[ { "name": "test", "percent": 0 } ]"""
    CrashConfig.computePatterns() assertIs emptyList()
  }

  @Test
  fun `not setting percent is the same as zero percent`() {
    every { FeatureFlags.crashPromptConfig() } returns """[ { "name": "test" } ]"""
    CrashConfig.computePatterns() assertIs emptyList()
  }

  @Test
  fun `ignore configs without a pattern`() {
    every { FeatureFlags.crashPromptConfig() } returns """[ { "percent": 100 } ]"""
    CrashConfig.computePatterns() assertIs emptyList()
  }

  @Test
  fun `ignore invalid json`() {
    every { FeatureFlags.crashPromptConfig() } returns "asdf"
    CrashConfig.computePatterns() assertIs emptyList()
  }

  @Test
  fun `ignore empty json`() {
    every { FeatureFlags.crashPromptConfig() } returns ""
    CrashConfig.computePatterns() assertIs emptyList()
  }
}
