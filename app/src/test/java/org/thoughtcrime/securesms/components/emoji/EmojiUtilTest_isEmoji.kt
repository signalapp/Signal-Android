package org.thoughtcrime.securesms.components.emoji

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.dependencies.MockApplicationDependencyProvider
import org.thoughtcrime.securesms.emoji.EmojiSource

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class EmojiUtilTest_isEmoji(private val input: String?, private val output: Boolean) {

  @Throws(Exception::class)
  @Test
  fun isEmoji() {
    if (!AppDependencies.isInitialized) {
      AppDependencies.init(ApplicationProvider.getApplicationContext(), MockApplicationDependencyProvider())
    }

    val source = EmojiSource.loadAssetBasedEmojis()

    mockkObject(EmojiSource) {
      every { EmojiSource.latest } returns source
      Assert.assertEquals(output, EmojiUtil.isEmoji(input))
    }
  }

  companion object {

    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters
    fun data(): Collection<Array<Any?>> {
      return listOf(
        arrayOf(null, false),
        arrayOf("", false),
        arrayOf("cat", false),
        arrayOf("ᑢᗩᖶ", false),
        arrayOf("♍︎♋︎⧫︎", false),
        arrayOf("ᑢ", false),
        arrayOf("¯\\_(ツ)_/¯", false),
        arrayOf("\uD83D\uDE0D", true), // Smiling face with heart-shaped eyes
        arrayOf("\uD83D\uDD77", true), // Spider
        arrayOf("\uD83E\uDD37", true), // Person shrugging
        arrayOf("\uD83E\uDD37\uD83C\uDFFF\u200D♂️", true), // Man shrugging dark skin tone
        arrayOf("\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66", true), // Family: Man, Woman, Girl, Boy
        arrayOf("\uD83D\uDC68\uD83C\uDFFB\u200D\uD83D\uDC69\uD83C\uDFFB\u200D\uD83D\uDC67\uD83C\uDFFB\u200D\uD83D\uDC66\uD83C\uDFFB", true), // Family - Man: Light Skin Tone, Woman: Light Skin Tone, Girl: Light Skin Tone, Boy: Light Skin Tone (NOTE: Not widely supported, good stretch test)
        arrayOf("\uD83D\uDE0Dhi", false), // Smiling face with heart-shaped eyes, text afterwards
        arrayOf("\uD83D\uDE0D ", false), // Smiling face with heart-shaped eyes, space afterwards
        arrayOf("\uD83D\uDE0D\uD83D\uDE0D", false) // Smiling face with heart-shaped eyes, twice
      )
    }
  }
}
