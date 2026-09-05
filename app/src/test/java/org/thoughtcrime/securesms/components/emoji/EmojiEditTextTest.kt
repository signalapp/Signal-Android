package org.thoughtcrime.securesms.components.emoji

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.testutil.MockSignalStoreRule
import org.thoughtcrime.securesms.util.TextSecurePreferences

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@LooperMode(LooperMode.Mode.LEGACY)
class EmojiEditTextTest {

  @get:Rule
  val signalStore = MockSignalStoreRule(relaxed = setOf(SettingsValues::class))

  private lateinit var editText: EmojiEditText

  @Before
  fun setUp() {
    mockkStatic(TextSecurePreferences::class)
    every { TextSecurePreferences.isIncognitoKeyboardEnabled(any()) } returns false
    every { signalStore.settings.isPreferSystemEmoji() } returns true

    val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Signal_DayNight)
    editText = EmojiEditText(context)
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `pasting a punycode URL converts it to unicode display form`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("url", "https://xn--gr-zia.org"))

    editText.onTextContextMenuItem(android.R.id.paste)

    assertEquals("https://grå.org", editText.text.toString())
  }
}
