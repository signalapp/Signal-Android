package org.thoughtcrime.securesms.linkpreview;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

public class LinkPreviewUtilTest {

  @Test
  public void isLegal_allAscii_noProtocol() {
    assertTrue(LinkPreviewUtil.isLegalUrl("google.com"));
  }

  @Test
  public void isLegal_allAscii_noProtocol_subdomain() {
    assertTrue(LinkPreviewUtil.isLegalUrl("foo.google.com"));
  }

  @Test
  public void isLegal_allAscii_subdomain() {
    assertTrue(LinkPreviewUtil.isLegalUrl("https://foo.google.com"));
  }

  @Test
  public void isLegal_allAscii_subdomain_path() {
    assertTrue(LinkPreviewUtil.isLegalUrl("https://foo.google.com/some/path.html"));
  }

  @Test
  public void isLegal_cyrillicHostAsciiTld() {
    assertFalse(LinkPreviewUtil.isLegalUrl("http://кц.com"));
  }

  @Test
  public void isLegal_cyrillicHostAsciiTld_noProtocol() {
    assertFalse(LinkPreviewUtil.isLegalUrl("кц.com"));
  }

  @Test
  public void isLegal_mixedHost_noProtocol() {
    assertFalse(LinkPreviewUtil.isLegalUrl("http://asĸ.com"));
  }

  @Test
  public void isLegal_cyrillicHostAndTld_noProtocol() {
    assertTrue(LinkPreviewUtil.isLegalUrl("кц.рф"));
  }

  @Test
  public void isLegal_cyrillicHostAndTld_asciiPath_noProtocol() {
    assertTrue(LinkPreviewUtil.isLegalUrl("кц.рф/some/path"));
  }

  @Test
  public void isLegal_cyrillicHostAndTld_asciiPath() {
    assertTrue(LinkPreviewUtil.isLegalUrl("https://кц.рф/some/path"));
  }

  @Test
  public void isLegal_asciiSubdomain_cyrillicHostAndTld() {
    assertFalse(LinkPreviewUtil.isLegalUrl("http://foo.кц.рф"));
  }

  @Test
  public void isLegal_emptyUrl() {
    assertFalse(LinkPreviewUtil.isLegalUrl(""));
  }
}
