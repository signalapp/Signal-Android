package org.thoughtcrime.securesms.components.emoji;

import android.app.Application;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.emoji.EmojiSource;
import org.thoughtcrime.securesms.keyvalue.InternalValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;

@Ignore("PowerMock failing")
@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
@PowerMockIgnore({"org.mockito.*", "org.robolectric.*", "android.*", "androidx.*", "org.powermock.*" })
@PrepareForTest({ApplicationDependencies.class, AttachmentSecretProvider.class, SignalStore.class, InternalValues.class})
public class EmojiUtilTest_isEmoji {

  public @Rule PowerMockRule rule = new PowerMockRule();

  private final String  input;
  private final boolean output;

  @ParameterizedRobolectricTestRunner.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
        {null, false},
        {"", false},
        {"cat", false},
        {"ᑢᗩᖶ", false},
        {"♍︎♋︎⧫︎", false},
        {"ᑢ", false},
        {"¯\\_(ツ)_/¯", false},
        {"\uD83D\uDE0D", true}, // Smiling face with heart-shaped eyes
        {"\uD83D\uDD77", true}, // Spider
        {"\uD83E\uDD37", true}, // Person shrugging
        {"\uD83E\uDD37\uD83C\uDFFF\u200D♂️", true}, // Man shrugging dark skin tone
        {"\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66", true}, // Family: Man, Woman, Girl, Boy
        {"\uD83D\uDC68\uD83C\uDFFB\u200D\uD83D\uDC69\uD83C\uDFFB\u200D\uD83D\uDC67\uD83C\uDFFB\u200D\uD83D\uDC66\uD83C\uDFFB", true}, // Family - Man: Light Skin Tone, Woman: Light Skin Tone, Girl: Light Skin Tone, Boy: Light Skin Tone (NOTE: Not widely supported, good stretch test)
        {"\uD83D\uDE0Dhi", false}, // Smiling face with heart-shaped eyes, text afterwards
        {"\uD83D\uDE0D ", false}, // Smiling face with heart-shaped eyes, space afterwards
        {"\uD83D\uDE0D\uD83D\uDE0D", false}, // Smiling face with heart-shaped eyes, twice
    });
  }


  public EmojiUtilTest_isEmoji(String input, boolean output) {
    this.input  = input;
    this.output = output;
  }

  @Test
  public void isEmoji() throws Exception {
    Application application = ApplicationProvider.getApplicationContext();

    PowerMockito.mockStatic(ApplicationDependencies.class);
    PowerMockito.when(ApplicationDependencies.getApplication()).thenReturn(application);
    PowerMockito.mockStatic(AttachmentSecretProvider.class);
    PowerMockito.when(AttachmentSecretProvider.getInstance(any())).thenThrow(RuntimeException.class);
    PowerMockito.whenNew(SignalStore.class).withAnyArguments().thenReturn(null);
    PowerMockito.mockStatic(SignalStore.class);
    PowerMockito.when(SignalStore.internalValues()).thenReturn(PowerMockito.mock(InternalValues.class));
    EmojiSource.refresh();

    assertEquals(output, EmojiUtil.isEmoji(input));
  }
}
