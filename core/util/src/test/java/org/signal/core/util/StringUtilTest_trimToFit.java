package org.signal.core.util;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public final class StringUtilTest_trimToFit {

  @Test
  public void testShortStringIsNotTrimmed() {
    assertEquals("Test string", StringUtil.trimToFit("Test string", 32));
    assertEquals("", StringUtil.trimToFit("", 32));
    assertEquals("aaaBBBCCC", StringUtil.trimToFit("aaaBBBCCC", 9));
  }

  @Test
  public void testNull() {
    assertEquals("", StringUtil.trimToFit(null, 0));
    assertEquals("", StringUtil.trimToFit(null, 1));
    assertEquals("", StringUtil.trimToFit(null, 10));
  }

  @Test
  public void testStringIsTrimmed() {
    assertEquals("Test stri", StringUtil.trimToFit("Test string", 9));
    assertEquals("aaaBBBCC", StringUtil.trimToFit("aaaBBBCCC", 8));
  }

  @Test
  public void testStringWithControlCharsIsTrimmed() {
    assertEquals("Test string\nwrap\r\nhere",
                 StringUtil.trimToFit("Test string\nwrap\r\nhere\tindent\n\n", 22));
  }

  @Test
  public void testAccentedCharactersAreTrimmedCorrectly() {
    assertEquals("", StringUtil.trimToFit("âëȋõṷ", 1));
    assertEquals("â", StringUtil.trimToFit("âëȋõṷ", 2));
    assertEquals("â", StringUtil.trimToFit("âëȋõṷ", 3));
    assertEquals("âë", StringUtil.trimToFit("âëȋõṷ", 4));
    assertEquals("The last characters take more than a byte in utf8 â",
                 StringUtil.trimToFit("The last characters take more than a byte in utf8 âëȋõṷ", 53));
    assertEquals("un quinzième jour en jaune apr", StringUtil.trimToFit("un quinzième jour en jaune après son épopée de 2019", 32));
    assertEquals("una vez se organizaron detrás l", StringUtil.trimToFit("una vez se organizaron detrás la ventaja nunca pasó de los 3 minutos.", 32));
  }

  @Test
  public void testCombinedAccentsAreTrimmedAsACharacter() {
    final String a = "a\u0302";
    final String e = "e\u0308";
    final String i = "i\u0311";
    final String o = "o\u0303";
    final String u = "u\u032d";
    assertEquals("", StringUtil.trimToFit(a + e + i + o + u, 1));
    assertEquals("", StringUtil.trimToFit(a + e + i + o + u, 2));
    assertEquals(a, StringUtil.trimToFit(a + e + i + o + u, 3));
    assertEquals(a, StringUtil.trimToFit(a + e + i + o + u, 4));
    assertEquals(a, StringUtil.trimToFit(a + e + i + o + u, 5));
    assertEquals(a + e, StringUtil.trimToFit(a + e + i + o + u, 6));
    assertEquals("The last characters take more than a byte in utf8 " + a,
                 StringUtil.trimToFit("The last characters take more than a byte in utf8 " + a + e + i + o + u, 53));
    assertEquals("un quinzie\u0300me jour en jaune apr", StringUtil.trimToFit("un quinzie\u0300me jour en jaune apre\u0300s son e\u0301pope\u0301e de 2019", 32));
    assertEquals("una vez se organizaron detra\u0301s ", StringUtil.trimToFit("una vez se organizaron detra\u0301s la ventaja nunca paso\u0301 de los 3 minutos.", 32));
  }

  @Test
  public void testCJKCharactersAreTrimmedCorrectly() {
    final String shin      = "\u4fe1";
    final String signal    = shin + "\u53f7";
    final String _private  = "\u79c1\u4eba";
    final String messenger = "\u4fe1\u4f7f";
    assertEquals("", StringUtil.trimToFit(signal, 1));
    assertEquals("", StringUtil.trimToFit(signal, 2));
    assertEquals(shin, StringUtil.trimToFit(signal, 3));
    assertEquals(shin, StringUtil.trimToFit(signal, 4));
    assertEquals(shin, StringUtil.trimToFit(signal, 5));
    assertEquals(signal, StringUtil.trimToFit(signal, 6));
    assertEquals(String.format("Signal %s Pr", signal),
                 StringUtil.trimToFit(String.format("Signal %s Private %s Messenger %s", signal, _private, messenger),
                                      16));
  }

  @Test
  public void testSurrogatePairsAreTrimmedCorrectly() {
    final String sword = "\uD841\uDF4F";
    assertEquals("", StringUtil.trimToFit(sword, 1));
    assertEquals("", StringUtil.trimToFit(sword, 2));
    assertEquals("", StringUtil.trimToFit(sword, 3));
    assertEquals(sword, StringUtil.trimToFit(sword, 4));

    final String so = "\ud869\uddf1";
    final String go = "\ud869\ude1a";
    assertEquals("", StringUtil.trimToFit(so + go, 1));
    assertEquals("", StringUtil.trimToFit(so + go, 2));
    assertEquals("", StringUtil.trimToFit(so + go, 3));
    assertEquals(so, StringUtil.trimToFit(so + go, 4));
    assertEquals(so, StringUtil.trimToFit(so + go, 5));
    assertEquals(so, StringUtil.trimToFit(so + go, 6));
    assertEquals(so, StringUtil.trimToFit(so + go, 7));
    assertEquals(so + go, StringUtil.trimToFit(so + go, 8));

    final String gClef = "\uD834\uDD1E";
    final String fClef = "\uD834\uDD22";
    assertEquals("", StringUtil.trimToFit(gClef + " " + fClef, 1));
    assertEquals("", StringUtil.trimToFit(gClef + " " + fClef, 2));
    assertEquals("", StringUtil.trimToFit(gClef + " " + fClef, 3));
    assertEquals(gClef, StringUtil.trimToFit(gClef + " " + fClef, 4));
    assertEquals(gClef + " ", StringUtil.trimToFit(gClef + " " + fClef, 5));
    assertEquals(gClef + " ", StringUtil.trimToFit(gClef + " " + fClef, 6));
    assertEquals(gClef + " ", StringUtil.trimToFit(gClef + " " + fClef, 7));
    assertEquals(gClef + " ", StringUtil.trimToFit(gClef + " " + fClef, 8));
    assertEquals(gClef + " " + fClef, StringUtil.trimToFit(gClef + " " + fClef, 9));
  }

  @Test
  public void testSimpleEmojiTrimming() {
    final String congrats = "\u3297";
    assertEquals("", StringUtil.trimToFit(congrats, 1));
    assertEquals("", StringUtil.trimToFit(congrats, 2));
    assertEquals(congrats, StringUtil.trimToFit(congrats, 3));

    final String eject = "\u23cf";
    assertEquals("", StringUtil.trimToFit(eject, 1));
    assertEquals("", StringUtil.trimToFit(eject, 2));
    assertEquals(eject, StringUtil.trimToFit(eject, 3));
  }

  @Test
  public void testEmojisSurrogatePairTrimming() {
    final String grape = "🍇";
    assertEquals("", StringUtil.trimToFit(grape, 1));
    assertEquals("", StringUtil.trimToFit(grape, 2));
    assertEquals("", StringUtil.trimToFit(grape, 3));
    assertEquals(grape, StringUtil.trimToFit(grape, 4));

    final String smile = "\uD83D\uDE42";
    assertEquals("", StringUtil.trimToFit(smile, 1));
    assertEquals("", StringUtil.trimToFit(smile, 2));
    assertEquals("", StringUtil.trimToFit(smile, 3));
    assertEquals(smile, StringUtil.trimToFit(smile, 4));

    final String check = "\u2714"; // Simple emoji
    assertEquals(check, StringUtil.trimToFit(check, 3));
    final String secret = "\u3299"; // Simple emoji
    assertEquals(secret, StringUtil.trimToFit(secret, 3));
    final String phoneWithArrow = "\uD83D\uDCF2"; // Surrogate Pair emoji
    assertEquals(phoneWithArrow, StringUtil.trimToFit(phoneWithArrow, 4));

    assertEquals(phoneWithArrow + ":",
                 StringUtil.trimToFit(phoneWithArrow + ":" + secret + ", " + check, 7));
    assertEquals(phoneWithArrow + ":" + secret,
                 StringUtil.trimToFit(phoneWithArrow + ":" + secret + ", " + check, 8));
    assertEquals(phoneWithArrow + ":" + secret + ",",
                 StringUtil.trimToFit(phoneWithArrow + ":" + secret + ", " + check, 9));
    assertEquals(phoneWithArrow + ":" + secret + ", ",
                 StringUtil.trimToFit(phoneWithArrow + ":" + secret + ", " + check, 10));
    assertEquals(phoneWithArrow + ":" + secret + ", ",
                 StringUtil.trimToFit(phoneWithArrow + ":" + secret + ", " + check, 11));
    assertEquals(phoneWithArrow + ":" + secret + ", ",
                 StringUtil.trimToFit(phoneWithArrow + ":" + secret + ", " + check, 12));
  }

  @Test
  public void testGraphemeClusterTrimming1() {
    assumeTrue(Build.VERSION.SDK_INT >= 24);

    final String alphas     = "AAAAABBBBBCCCCCDDDDDEEEEE";
    final String wavingHand = "\uD83D\uDC4B";
    final String mediumDark = "\uD83C\uDFFE";
    assertEquals(alphas, StringUtil.trimToFit(alphas + wavingHand + mediumDark, 32));
    assertEquals(alphas + wavingHand + mediumDark, StringUtil.trimToFit(alphas + wavingHand + mediumDark, 33));

    final String pads           = "abcdefghijklm";
    final String frowningPerson = "\uD83D\uDE4D";
    final String female         = "\u200D\u2640\uFE0F";
    assertEquals(pads + frowningPerson + female,
                 StringUtil.trimToFit(pads + frowningPerson + female, 26));
    assertEquals(pads + "n",
                 StringUtil.trimToFit(pads + "n" + frowningPerson + female, 26));

    final String pads1      = "abcdef";
    final String mediumSkin = "\uD83C\uDFFD️";
    assertEquals(pads1 + frowningPerson + mediumSkin + female,
                 StringUtil.trimToFit(pads1 + frowningPerson + mediumSkin + female, 26));
    assertEquals(pads1 + "g",
                 StringUtil.trimToFit(pads1 + "g" + frowningPerson + mediumSkin + female, 26));
  }

  @Test
  public void testGraphemeClusterTrimming2() {
    assumeTrue(Build.VERSION.SDK_INT >= 24);

    final String woman          = "\uD83D\uDC69";
    final String mediumDarkSkin = "\uD83C\uDFFE";
    final String joint          = "\u200D";
    final String hands          = "\uD83E\uDD1D";
    final String man            = "\uD83D\uDC68";
    final String lightSkin      = "\uD83C\uDFFB";

    assertEquals(woman + mediumDarkSkin + joint + hands + joint + man + lightSkin,
                 StringUtil.trimToFit(woman + mediumDarkSkin + joint + hands + joint + man + lightSkin, 26));
    assertEquals("a",
                 StringUtil.trimToFit("a" + woman + mediumDarkSkin + joint + hands + joint + man + lightSkin, 26));

    final String pads       = "abcdefghijk";
    final String wheelchair = "\uD83E\uDDBC";
    assertEquals(pads + man + lightSkin + joint + wheelchair,
                 StringUtil.trimToFit(pads + man + lightSkin + joint + wheelchair, 26));
    assertEquals(pads + "l",
                 StringUtil.trimToFit(pads + "l" + man + lightSkin + joint + wheelchair, 26));

    final String girl = "\uD83D\uDC67";
    final String boy  = "\uD83D\uDC66";
    assertEquals(man + mediumDarkSkin + joint + man + joint + girl + lightSkin + joint + boy,
                 StringUtil.trimToFit(man + mediumDarkSkin + joint + man + joint + girl + lightSkin + joint + boy, 33));
    assertEquals("a",
                 StringUtil.trimToFit("a" + man + mediumDarkSkin + joint + man + joint + girl + lightSkin + joint + boy, 33));
  }
}
