package org.signal.core.util.logging;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class LogTest {

  @Test
  public void tag_short_class_name() {
    assertEquals("MyClass", Log.tag(MyClass.class));
  }

  @Test
  public void tag_23_character_class_name() {
    String tag = Log.tag(TwentyThreeCharacters23.class);
    assertEquals("TwentyThreeCharacters23", tag);
    assertEquals(23, tag.length());
  }

  @Test
  public void tag_24_character_class_name() {
    assertEquals(24, TwentyFour24Characters24.class.getSimpleName().length());
    String tag = Log.tag(TwentyFour24Characters24.class);
    assertEquals("TwentyFour24Characters2", tag);
    assertEquals(23, Log.tag(TwentyThreeCharacters23.class).length());
  }

  private class MyClass {
  }

  private class TwentyThreeCharacters23 {
  }

  private class TwentyFour24Characters24 {
  }
}
