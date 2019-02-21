package org.thoughtcrime.securesms.linkpreview;

import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

public class LinkPreviewUtilTest {

  @Test
  public void isLegal_allLatin() {
    assertTrue(LinkPreviewUtil.isLegalUrl("https://signal.org"));
  }

  @Test
  public void isLegal_latinAndCyrillic() {
    assertFalse(LinkPreviewUtil.isLegalUrl("https://www.аmazon.com"));
  }

  @Test
  public void isLegal_latinAndGreek() {
    assertFalse(LinkPreviewUtil.isLegalUrl("https://www.αpple.com"));
  }
}
