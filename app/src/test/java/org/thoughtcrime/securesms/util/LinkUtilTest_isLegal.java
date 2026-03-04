package org.thoughtcrime.securesms.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("NewClassNamingConvention")
@RunWith(Parameterized.class)
public class LinkUtilTest_isLegal {

  private final String  input;
  private final boolean output;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
        { "google.com",                            true  },
        { "foo.google.com",                        true  },
        { "https://foo.google.com",                true  },
        { "https://foo.google.com/some/path.html", true  },
        { "кц.рф",                                 true  },
        { "https://кц.рф/some/path",               true  },
        { "https://abcdefg.onion",                 true  },
        { "https://abcdefg.i2p",                   true  },
        { "http://кц.com",                         false },
        { "кц.com",                                false },
        { "http://asĸ.com",                        true  }, // ĸ (U+0138) is Unicode script LATIN
        { "http://foo.кц.рф",                      false },
        { "кц.рф\u202C",                           false },
        { "кц.рф\u202D",                           false },
        { "кц.рф\u202E",                           false },
        { "кц.рф\u2500",                           false },
        { "кц.рф\u25AA",                           false },
        { "кц.рф\u25FF",                           false },
        { "",                                      false },
        { "cool.example",                          true },
        { "cool.example.com",                      true },
        { "cool.example.net",                      true },
        { "cool.example.org",                      true },
        { "cool.invalid",                          true },
        { "cool.localhost",                        true },
        { "localhost",                             true },
        { "https://localhost",                     true },
        { "cool.test",                             true },
        { "grå.org",                               true }, // å is Latin script
        { "münchen.de",                            true }, // ü is Latin script
        { "慕田峪长城.网址",                         true }, // Great Wall site
        // Мышкин is the idiot in Dostoyevsky's book.
        { "Мышкин.рф",                             true }, // Cyrillic к U+043A
        { "Мышκин.рф",                             false }, // Greek κ U+03BA
        { "https://github.com/signalapp/Signal-Android/compare/v6.23.2...v6.23.3", true }
    });
  }

  public LinkUtilTest_isLegal(String input, boolean output) {
    this.input  = input;
    this.output = output;
  }

  @Test
  public void isLegal() {
    assertEquals(output, LinkUtil.isLegalUrl(input));
  }
}
