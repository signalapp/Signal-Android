package org.thoughtcrime.securesms.crash

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.assertIs
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
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

  @Rule
  @JvmField
  val mockitoRule: MockitoRule = MockitoJUnit.rule()

  @Mock
  private lateinit var featureFlags: MockedStatic<FeatureFlags>

  @Before
  fun setup() {
    if (!ApplicationDependencies.isInitialized()) {
      ApplicationDependencies.init(ApplicationProvider.getApplicationContext(), MockApplicationDependencyProvider())
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
    `when`(FeatureFlags.crashPromptConfig()).thenReturn("""[ { "name": "test", "percent": 100 } ]""")
    CrashConfig.computePatterns() assertIs listOf(CrashConfig.CrashPattern(namePattern = "test"))
  }

  @Test
  fun `simple message pattern`() {
    `when`(FeatureFlags.crashPromptConfig()).thenReturn("""[ { "message": "test", "percent": 100 } ]""")
    CrashConfig.computePatterns() assertIs listOf(CrashConfig.CrashPattern(messagePattern = "test"))
  }

  @Test
  fun `simple stackTrace pattern`() {
    `when`(FeatureFlags.crashPromptConfig()).thenReturn("""[ { "stackTrace": "test", "percent": 100 } ]""")
    CrashConfig.computePatterns() assertIs listOf(CrashConfig.CrashPattern(stackTracePattern = "test"))
  }

  @Test
  fun `all fields set`() {
    `when`(FeatureFlags.crashPromptConfig()).thenReturn("""[ { "name": "test1", "message": "test2", "stackTrace": "test3", "percent": 100 } ]""")
    CrashConfig.computePatterns() assertIs listOf(CrashConfig.CrashPattern(namePattern = "test1", messagePattern = "test2", stackTracePattern = "test3"))
  }

  @Test
  fun `multiple configs`() {
    `when`(FeatureFlags.crashPromptConfig()).thenReturn(
      """
      [ 
        { "name": "test1", "percent": 100 },
        { "message": "test2", "percent": 100 },
        { "stackTrace": "test3", "percent": 100 }
      ]
      """
    )

    CrashConfig.computePatterns() assertIs listOf(
      CrashConfig.CrashPattern(namePattern = "test1"),
      CrashConfig.CrashPattern(messagePattern = "test2"),
      CrashConfig.CrashPattern(stackTracePattern = "test3")
    )
  }

  @Test
  fun `empty fields are considered null`() {
    `when`(FeatureFlags.crashPromptConfig()).thenReturn(
      """
      [ 
        { "name": "", "percent": 100 },
        { "name": "test1", "message": "", "percent": 100 },
        { "message": "test2", "stackTrace": "", "percent": 100 }
      ]
      """
    )

    CrashConfig.computePatterns() assertIs listOf(
      CrashConfig.CrashPattern(namePattern = "test1"),
      CrashConfig.CrashPattern(messagePattern = "test2")
    )
  }

  @Test
  fun `ignore zero percent`() {
    `when`(FeatureFlags.crashPromptConfig()).thenReturn("""[ { "name": "test", "percent": 0 } ]""")
    CrashConfig.computePatterns() assertIs emptyList()
  }

  @Test
  fun `not setting percent is the same as zero percent`() {
    `when`(FeatureFlags.crashPromptConfig()).thenReturn("""[ { "name": "test" } ]""")
    CrashConfig.computePatterns() assertIs emptyList()
  }

  @Test
  fun `ignore configs without a pattern`() {
    `when`(FeatureFlags.crashPromptConfig()).thenReturn("""[ { "percent": 100 } ]""")
    CrashConfig.computePatterns() assertIs emptyList()
  }

  @Test
  fun `ignore invalid json`() {
    `when`(FeatureFlags.crashPromptConfig()).thenReturn("asdf")
    CrashConfig.computePatterns() assertIs emptyList()
  }

  @Test
  fun `ignore empty json`() {
    `when`(FeatureFlags.crashPromptConfig()).thenReturn("")
    CrashConfig.computePatterns() assertIs emptyList()
  }
}
