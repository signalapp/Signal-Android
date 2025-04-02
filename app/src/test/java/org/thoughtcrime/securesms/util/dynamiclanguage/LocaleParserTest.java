package org.thoughtcrime.securesms.util.dynamiclanguage;

import android.app.Application;
import android.content.res.Resources;

import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.thoughtcrime.securesms.BuildConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public final class LocaleParserTest {

  @Ignore("does not pass on jdk17")
  @Test
  public void findBestMatchingLocaleForLanguage_all_build_config_languages_can_be_resolved() {
    for (String lang : buildConfigLanguages()) {
      Locale locale = LocaleParser.findBestMatchingLocaleForLanguage(lang, getSystemLocales());
      assertEquals(lang, locale.toString());
    }
  }

  @Test
  @Config(qualifiers = "fr")
  public void findBestMatchingLocaleForLanguage_a_non_build_config_language_defaults_to_device_value_which_is_supported_directly() {
    String unsupportedLanguage = getUnsupportedLanguage();
    assertEquals(Locale.FRENCH, LocaleParser.findBestMatchingLocaleForLanguage(unsupportedLanguage, getSystemLocales()));
  }

  @Test
  @Config(qualifiers = "en-rCA")
  public void findBestMatchingLocaleForLanguage_a_non_build_config_language_defaults_to_device_value_which_is_not_supported_directly() {
    String unsupportedLanguage = getUnsupportedLanguage();
    assertEquals(Locale.CANADA, LocaleParser.findBestMatchingLocaleForLanguage(unsupportedLanguage, getSystemLocales()));
  }

  private static String getUnsupportedLanguage() {
    String unsupportedLanguage = "ae";
    assertFalse("Language should be an unsupported one", buildConfigLanguages().contains(unsupportedLanguage));
    return unsupportedLanguage;
  }

  private static List<String> buildConfigLanguages() {
    return Arrays.asList(BuildConfig.LANGUAGES);
  }

  private static LocaleListCompat getSystemLocales() {
    return ConfigurationCompat.getLocales(Resources.getSystem().getConfiguration());
  }
}
