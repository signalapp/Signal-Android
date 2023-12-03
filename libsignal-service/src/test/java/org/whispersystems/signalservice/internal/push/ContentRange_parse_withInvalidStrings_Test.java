package org.whispersystems.signalservice.internal.push;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertFalse;

@RunWith(Parameterized.class)
public final class ContentRange_parse_withInvalidStrings_Test {

  @Parameterized.Parameter(0)
  public String input;

  @Parameterized.Parameters(name = "Content-Range: \"{0}\"")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      { null },
      { "" },
      { "23-45/67" },
      { "ersions 23-45/67" },
      { "versions 23-45" },
      { "versions a-b/c" }
    });
  }

  @Test
  public void parse_should_be_absent() {
    assertFalse(ContentRange.parse(input).isPresent());
  }
}
