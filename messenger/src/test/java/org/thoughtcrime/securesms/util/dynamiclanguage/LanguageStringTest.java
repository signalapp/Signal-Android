package org.thoughtcrime.securesms.util.dynamiclanguage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public final class LanguageStringTest {

  private final Locale expected;
  private final String input;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{

    /* Language */
    { new Locale("en"), "en" },
    { new Locale("de"), "de" },
    { new Locale("fr"), "FR" },

    /* Language and region */
    { new Locale("en", "US"), "en_US" },
    { new Locale("es", "US"), "es_US" },
    { new Locale("es", "MX"), "es_MX" },
    { new Locale("es", "MX"), "es_mx" },
    { new Locale("de", "DE"), "de_DE" },

    /* Not parsable input */
    { null, null },
    { null, "" },
    { null, "zz" },
    { null, "zz_ZZ" },
    { null, "fr_ZZ" },
    { null, "zz_FR" },

    });
  }

  public LanguageStringTest(Locale expected, String input) {
    this.expected = expected;
    this.input    = input;
  }

  @Test
  public void parse() {
    assertEquals(expected, LanguageString.parseLocale(input));
  }
}
