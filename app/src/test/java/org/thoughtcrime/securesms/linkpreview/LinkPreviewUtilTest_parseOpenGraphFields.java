package org.thoughtcrime.securesms.linkpreview;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Arrays;
import java.util.Collection;

import static junit.framework.TestCase.assertEquals;

@RunWith(Parameterized.class)
public class LinkPreviewUtilTest_parseOpenGraphFields {

  private final String html;
  private final String title;
  private final String imageUrl;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
        // Normal
        { "<meta content=\"Daily Bugle\" property=\"og:title\">\n" +
          "<meta content=\"https://images.com/my-image.jpg\" property=\"og:image\">",
            "Daily Bugle",
            "https://images.com/my-image.jpg"},

        // Swap property orders
        { "<meta property=\"og:title\" content=\"Daily Bugle\">\n" +
          "<meta property=\"og:image\" content=\"https://images.com/my-image.jpg\">",
            "Daily Bugle",
            "https://images.com/my-image.jpg"},

        // Funny spacing
        { "< meta   property   = \"og:title\" content =  \"Daily Bugle\"  >\n\n" +
          "<  meta  property = \"og:image\" content   =\"https://images.com/my-image.jpg\" >",
            "Daily Bugle",
            "https://images.com/my-image.jpg"},

        // Garbage in various places
        { "<meta property=\"og:title\" content=\"Daily Bugle\">\n" +
          "asdfjkl\n" +
          "<body>idk</body>\n" +
          "<script type=\"text/javascript\">var a = </script>\n" +
          "<meta property=\"og:image\" content=\"https://images.com/my-image.jpg\">",
            "Daily Bugle",
            "https://images.com/my-image.jpg"},

        // Missing image
        { "<meta content=\"Daily Bugle\" property=\"og:title\">",
            "Daily Bugle",
            null},

        // Missing title
        { "<meta content=\"https://images.com/my-image.jpg\" property=\"og:image\">",
            null,
            "https://images.com/my-image.jpg"},

        // Has everything
        { "<meta property=\"og:title\" content =  \"Daily Bugle\">\n" +
          "<title>Daily Bugle HTML</title>\n" +
          "<meta property=\"og:image\" content=\"https://images.com/my-image.jpg\">\n" +
          "<link rel=\"icon\" href=\"https://images.com/favicon.png\" />",
            "Daily Bugle",
            "https://images.com/my-image.jpg"},

        // Fallback to HTML title
        { "<title>Daily Bugle HTML</title>\n" +
          "<meta property=\"og:image\" content=\"https://images.com/my-image.jpg\">\n" +
          "<link rel=\"icon\" href=\"https://images.com/favicon.png\" />",
            "Daily Bugle HTML",
            "https://images.com/my-image.jpg"},

        // Fallback to favicon
        { "<meta property=\"og:title\" content =  \"Daily Bugle\">\n" +
          "<title>Daily Bugle HTML</title>\n" +
          "<link rel=\"icon\" href=\"https://images.com/favicon.png\" />",
            "Daily Bugle",
            "https://images.com/favicon.png"},

        // Fallback to HTML title and favicon
        { "<title>Daily Bugle HTML</title>\n" +
          "<link rel=\"icon\" href=\"https://images.com/favicon.png\" />",
            "Daily Bugle HTML",
            "https://images.com/favicon.png"},

        // Different favicon formatting
        { "<title>Daily Bugle HTML</title>\n" +
          "<link rel=\"shortcut icon\" href=\"https://images.com/favicon.png\" />",
            "Daily Bugle HTML",
            "https://images.com/favicon.png"},
    });
  }

  public LinkPreviewUtilTest_parseOpenGraphFields(String html, String title, String imageUrl) {
    this.html     = html;
    this.title    = title;
    this.imageUrl = imageUrl;
  }

  @Test
  public void parseOpenGraphFields() {
    LinkPreviewUtil.OpenGraph openGraph = LinkPreviewUtil.parseOpenGraphFields(html, html -> html);
    assertEquals(Optional.fromNullable(title), openGraph.getTitle());
    assertEquals(Optional.fromNullable(imageUrl), openGraph.getImageUrl());
  }
}
