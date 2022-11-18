package org.thoughtcrime.securesms.recipients

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PowerMockIgnore
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.rule.PowerMockRule
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.ChatColorsValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.WallpaperValues
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
@PowerMockIgnore("org.mockito.*", "org.robolectric.*", "android.*", "androidx.*")
@PrepareForTest(ApplicationDependencies::class, AttachmentSecretProvider::class, SignalStore::class, WallpaperValues::class, ChatColorsValues::class)
abstract class BaseRecipientTest {
  @Rule
  @JvmField
  var rule = PowerMockRule()

  @Before
  fun superSetUp() {
    val application = ApplicationProvider.getApplicationContext<Application>()

    PowerMockito.mockStatic(ApplicationDependencies::class.java)
    PowerMockito.`when`(ApplicationDependencies.getApplication()).thenReturn(application)
    PowerMockito.mockStatic(AttachmentSecretProvider::class.java)
    PowerMockito.`when`(AttachmentSecretProvider.getInstance(ArgumentMatchers.any())).thenThrow(IOException::class.java)
    PowerMockito.whenNew(SignalStore::class.java).withAnyArguments().thenReturn(null)
    PowerMockito.mockStatic(SignalStore::class.java)
  }
}
