package org.thoughtcrime.securesms.components.emoji;

import android.app.Application;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public class EmojiUtilTest_isEmoji {

  private final String  input;
  private final boolean output;

  @ParameterizedRobolectricTestRunner.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
        { null, false },
        { "", false },
        { "cat", false },
        { "ᑢᗩᖶ", false },
        { "♍︎♋︎⧫︎", false },
        { "ᑢ", false },
        { "¯\\_(ツ)_/¯", false},
        { "\uD83D\uDE0D", true }, // Smiling face with heart-shaped eyes
        { "\uD83D\uDD77", true }, // Spider
        { "\uD83E\uDD37", true }, // Person shrugging
        { "\uD83E\uDD37\uD83C\uDFFF\u200D♂️", true }, // Man shrugging dark skin tone
        { "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66", true }, // Family: Man, Woman, Girl, Boy
        { "\uD83D\uDC68\uD83C\uDFFB\u200D\uD83D\uDC69\uD83C\uDFFB\u200D\uD83D\uDC67\uD83C\uDFFB\u200D\uD83D\uDC66\uD83C\uDFFB", true }, // Family - Man: Light Skin Tone, Woman: Light Skin Tone, Girl: Light Skin Tone, Boy: Light Skin Tone (NOTE: Not widely supported, good stretch test)
        { "\uD83D\uDE0Dhi", false }, // Smiling face with heart-shaped eyes, text afterwards
        { "\uD83D\uDE0D ", false }, // Smiling face with heart-shaped eyes, space afterwards
        { "\uD83D\uDE0D\uD83D\uDE0D", false }, // Smiling face with heart-shaped eyes, twice
    });
  }


  public EmojiUtilTest_isEmoji(String input, boolean output) {
    this.input  = input;
    this.output = output;
  }

  @Test
  public void isEmoji() {
    Context context = ApplicationProvider.getApplicationContext();

    assertEquals(output, EmojiUtil.isEmoji(context, input));
  }
}
