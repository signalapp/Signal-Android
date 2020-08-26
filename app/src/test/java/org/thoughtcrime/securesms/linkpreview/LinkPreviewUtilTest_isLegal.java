package org.thoughtcrime.securesms.linkpreview;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static junit.framework.TestCase.assertEquals;

@RunWith(Parameterized.class)
public class LinkPreviewUtilTest_isLegal {

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
        { "http://кц.com",                         false },
        { "кц.com",                                false },
        { "http://asĸ.com",                        false },
        { "http://foo.кц.рф",                      false },
        { "https://abcdefg.onion",                 false },
        { "https://abcdefg.i2p",                   false },
        { "",                                      false }
    });
  }

  public LinkPreviewUtilTest_isLegal(String input, boolean output) {
    this.input  = input;
    this.output = output;
  }

  @Test
  public void isLegal() {
    assertEquals(output, LinkPreviewUtil.isLegalUrl(input));
  }
}
