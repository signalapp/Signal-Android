package org.thoughtcrime.securesms.util.dynamiclanguage;

import android.app.Application;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import network.loki.messenger.BuildConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

//FIXME AC: This test group is outdated.
@Ignore("This test group uses outdated instrumentation and needs a migration to modern tools.")
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public final class LocaleParserTest {

  @Test
  public void findBestMatchingLocaleForLanguage_all_build_config_languages_can_be_resolved() {
    for (String lang : buildConfigLanguages()) {
      Locale locale = LocaleParser.findBestMatchingLocaleForLanguage(lang);
      assertEquals(lang, locale.toString());
    }
  }

  @Test
  @Config(qualifiers = "fr")
  public void findBestMatchingLocaleForLanguage_a_non_build_config_language_defaults_to_device_value_which_is_supported_directly() {
    String unsupportedLanguage = getUnsupportedLanguage();
    assertEquals(Locale.FRENCH, LocaleParser.findBestMatchingLocaleForLanguage(unsupportedLanguage));
  }

  @Test
  @Config(qualifiers = "en-rCA")
  public void findBestMatchingLocaleForLanguage_a_non_build_config_language_defaults_to_device_value_which_is_not_supported_directly() {
    String unsupportedLanguage = getUnsupportedLanguage();
    assertEquals(Locale.CANADA, LocaleParser.findBestMatchingLocaleForLanguage(unsupportedLanguage));
  }

  private static String getUnsupportedLanguage() {
    String unsupportedLanguage = "af";
    assertFalse("Language should be an unsupported one", buildConfigLanguages().contains(unsupportedLanguage));
    return unsupportedLanguage;
  }

  private static List<String> buildConfigLanguages() {
    return Arrays.asList(BuildConfig.LANGUAGES);
  }
}
