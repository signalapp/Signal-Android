package org.signal.locale;

import android.app.Application;
import android.content.res.Resources;

import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public final class LocaleParserTest {

  private static final String[] SUPPORTED = { "en", "de", "fr", "pt_BR" };

  @Test
  public void findBestMatchingLocale_all_supported_languages_can_be_resolved() {
    for (String lang : SUPPORTED) {
      Locale locale = LocaleParser.findBestMatchingLocale(LanguageString.parseLocale(lang), getSystemLocales(), SUPPORTED);
      assertEquals(lang, locale.toString());
    }
  }

  @Test
  @Config(qualifiers = "fr")
  public void findBestMatchingLocale_an_unsupported_language_defaults_to_device_value_which_is_supported_directly() {
    String unsupportedLanguage = getUnsupportedLanguage();
    assertEquals(Locale.FRENCH, LocaleParser.findBestMatchingLocale(LanguageString.parseLocale(unsupportedLanguage), getSystemLocales(), SUPPORTED));
  }

  @Test
  @Config(qualifiers = "en-rCA")
  public void findBestMatchingLocale_an_unsupported_language_defaults_to_device_value_which_is_not_supported_directly() {
    String unsupportedLanguage = getUnsupportedLanguage();
    assertEquals(Locale.CANADA, LocaleParser.findBestMatchingLocale(LanguageString.parseLocale(unsupportedLanguage), getSystemLocales(), SUPPORTED));
  }

  private static String getUnsupportedLanguage() {
    String unsupportedLanguage = "ae";
    assertFalse("Language should be an unsupported one", Arrays.asList(SUPPORTED).contains(unsupportedLanguage));
    return unsupportedLanguage;
  }

  private static LocaleListCompat getSystemLocales() {
    return ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration());
  }
}
