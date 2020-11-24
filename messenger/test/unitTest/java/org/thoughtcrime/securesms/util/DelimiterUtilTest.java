package org.thoughtcrime.securesms.util;


import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class DelimiterUtilTest {

  @Before
  public void setup() {}

  @Test
  public void testEscape() {
    assertEquals(DelimiterUtil.escape("MTV Music", ' '), "MTV\\ Music");
    assertEquals(DelimiterUtil.escape("MTV  Music", ' '), "MTV\\ \\ Music");

    assertEquals(DelimiterUtil.escape("MTV,Music", ','), "MTV\\,Music");
    assertEquals(DelimiterUtil.escape("MTV,,Music", ','), "MTV\\,\\,Music");

    assertEquals(DelimiterUtil.escape("MTV Music", '+'), "MTV Music");
  }

  @Test
  public void testSplit() {
    String[] parts = DelimiterUtil.split("MTV\\ Music", ' ');
    assertEquals(parts.length, 1);
    assertEquals(parts[0], "MTV\\ Music");

    parts = DelimiterUtil.split("MTV Music", ' ');
    assertEquals(parts.length, 2);
    assertEquals(parts[0], "MTV");
    assertEquals(parts[1], "Music");
  }

  @Test
  public void testEscapeSplit() {
    String   input        = "MTV Music";
    String   intermediate = DelimiterUtil.escape(input, ' ');
    String[] parts        = DelimiterUtil.split(intermediate, ' ');

    assertEquals(parts.length, 1);
    assertEquals(parts[0], "MTV\\ Music");
    assertEquals(DelimiterUtil.unescape(parts[0], ' '), "MTV Music");

    input        = "MTV\\ Music";
    intermediate = DelimiterUtil.escape(input, ' ');
    parts        = DelimiterUtil.split(intermediate, ' ');

    assertEquals(parts.length, 1);
    assertEquals(parts[0], "MTV\\\\ Music");
    assertEquals(DelimiterUtil.unescape(parts[0], ' '), "MTV\\ Music");
  }

}
