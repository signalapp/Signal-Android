package org.thoughtcrime.securesms.recipients

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
abstract class BaseRecipientTest {

  @Rule
  @JvmField
  val mockitoRule: MockitoRule = MockitoJUnit.rule()

  @Mock
  private lateinit var applicationDependenciesStaticMock: MockedStatic<ApplicationDependencies>

  @Mock
  private lateinit var attachmentSecretProviderStaticMock: MockedStatic<AttachmentSecretProvider>

  @Mock
  private lateinit var signalStoreStaticMock: MockedStatic<SignalStore>

  @Mock
  private lateinit var mockedSignalStoreConstruction: MockedConstruction<SignalStore>

  @Before
  fun superSetUp() {
    val application = ApplicationProvider.getApplicationContext<Application>()

    `when`(ApplicationDependencies.getApplication()).thenReturn(application)
    `when`(AttachmentSecretProvider.getInstance(ArgumentMatchers.any())).thenThrow(RuntimeException::class.java)
  }
}
