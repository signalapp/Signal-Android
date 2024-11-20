package org.thoughtcrime.securesms.util;


import android.text.TextUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;

public class DelimiterUtilTest {

  @Rule
  public MockitoRule rule = MockitoJUnit.rule();

  @Mock
  private MockedStatic<TextUtils> textUtilsMockedStatic;

  @Before
  public void setup() {
    textUtilsMockedStatic.when(() -> TextUtils.isEmpty(anyString())).thenAnswer((Answer<Boolean>) invocation -> {
      if (invocation.getArguments()[0] == null) return true;
      return ((String) invocation.getArguments()[0]).isEmpty();
    });
  }

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
