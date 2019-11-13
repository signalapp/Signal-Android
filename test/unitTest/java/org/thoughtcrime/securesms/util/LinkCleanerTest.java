package org.thoughtcrime.securesms.util;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class LinkCleanerTest {

  @Test
  public void testCleanText_linkWithTrackersOnly() {
    String dirtyLink = "https://www.this-test.org/index.php?fbclid=feA9DGOf842eEA_12d&utm_source=wonderland";
    String cleanLink = "https://www.this-test.org/index.php";
    assertEquals(cleanLink, LinkCleaner.cleanText(dirtyLink));
  }

  @Test
  public void cleanText_linkWithoutTrackers() {
    String link = "https://en.wikipedia.org/w/index.php?title=Special:UserLogin&returnto=Wikipedia";
    assertEquals(link, LinkCleaner.cleanText(link));
  }

  @Test
  public void cleanText_linkWithMixedContent() {
    String dirtyLink = "https://www.random-forum.com/post.php?igshid=ex3T8Ann72_tE&id=123456&utm_media=social";
    String cleanLink = "https://www.random-forum.com/post.php?id=123456";
    assertEquals(cleanLink, LinkCleaner.cleanText(dirtyLink));
  }

  @Test
  public void cleanText_longText() {
    String dirtyText = "https://hello.world/projects/template.html?ocid=IwAR38aoZHqjWAbwzwrpcY7yLs" +
        "http://i.am.a.clean/link.php?user=Foo&feature=bar" +
        " Loremipsumhttps://instathing.com/image.htm?utm_campaign=buffer dolor sit amet.";
    String cleanText = "https://hello.world/projects/template.html" +
        "http://i.am.a.clean/link.php?user=Foo&feature=bar" +
        " Loremipsumhttps://instathing.com/image.htm dolor sit amet.";
    assertEquals(cleanText, LinkCleaner.cleanText(dirtyText));
  }
}
