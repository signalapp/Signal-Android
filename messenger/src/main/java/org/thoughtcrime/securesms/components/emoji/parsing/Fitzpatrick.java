package org.thoughtcrime.securesms.components.emoji.parsing;


public enum Fitzpatrick {
  /**
   * Fitzpatrick modifier of type 1/2 (pale white/white)
   */
  TYPE_1_2("\uD83C\uDFFB"),

  /**
   * Fitzpatrick modifier of type 3 (cream white)
   */
  TYPE_3("\uD83C\uDFFC"),

  /**
   * Fitzpatrick modifier of type 4 (moderate brown)
   */
  TYPE_4("\uD83C\uDFFD"),

  /**
   * Fitzpatrick modifier of type 5 (dark brown)
   */
  TYPE_5("\uD83C\uDFFE"),

  /**
   * Fitzpatrick modifier of type 6 (black)
   */
  TYPE_6("\uD83C\uDFFF");

  /**
   * The unicode representation of the Fitzpatrick modifier
   */
  public final String unicode;

  Fitzpatrick(String unicode) {
    this.unicode = unicode;
  }


  public static Fitzpatrick fitzpatrickFromUnicode(CharSequence unicode, int index) {
    for (Fitzpatrick v : values()) {
      boolean match = true;

      for (int i=0;i<v.unicode.toCharArray().length;i++) {
        if (v.unicode.toCharArray()[i] != unicode.charAt(index + i)) {
          match = false;
        }
      }

      if (match) return v;
    }

    return null;
  }

  public static Fitzpatrick fitzpatrickFromType(String type) {
    try {
      return Fitzpatrick.valueOf(type.toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}

