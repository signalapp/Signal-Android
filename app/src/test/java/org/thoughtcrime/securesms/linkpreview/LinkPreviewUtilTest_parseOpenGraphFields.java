package org.thoughtcrime.securesms.linkpreview;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public class LinkPreviewUtilTest_parseOpenGraphFields {

  private final String html;
  private final String title;
  private final String description;
  private final long   date;
  private final String imageUrl;

  @ParameterizedRobolectricTestRunner.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
        // Normal
        { "<meta content=\"Daily Bugle\" property=\"og:title\">\n" +
          "<meta content=\"https://images.com/my-image.jpg\" property=\"og:image\">" +
          "<meta content=\"A newspaper\" property=\"og:description\">" +
          "<meta content=\"1991-12-30T00:00:00+00:00\" property=\"og:published_time\">",
            "Daily Bugle",
            "A newspaper",
            694051200000L,
            "https://images.com/my-image.jpg"},

        // Swap property orders
        { "<meta property=\"og:title\" content=\"Daily Bugle\">\n" +
          "<meta property=\"og:image\" content=\"https://images.com/my-image.jpg\">" +
          "<meta property=\"og:description\" content=\"A newspaper\">" +
          "<meta property=\"og:published_time\" content=\"1991-12-30T00:00:00+00:00\">",
            "Daily Bugle",
            "A newspaper",
            694051200000L,
            "https://images.com/my-image.jpg"},

        // Funny spacing
        { "< meta   property   = \"og:title\" content =  \"Daily Bugle\"  >\n\n" +
          "<  meta  property = \"og:image\" content   =\"https://images.com/my-image.jpg\" >" +
          "< meta property =\"og:description\" content =\"A newspaper\">   " +
          "<  meta property   =\"og:published_time\"   content=   \"1991-12-30T00:00:00+00:00\"> ",
            "Daily Bugle",
            "A newspaper",
            694051200000L,
            "https://images.com/my-image.jpg"},

        // Garbage in various places
        { "<meta property=\"og:title\" content=\"Daily Bugle\">\n" +
          "asdfjkl\n" +
          "<body>idk</body>\n" +
          "<script type=\"text/javascript\">var a = </script>\n" +
          "<meta property=\"og:image\" content=\"https://images.com/my-image.jpg\">",
            "Daily Bugle",
            null,
            0,
            "https://images.com/my-image.jpg"},

        // Missing image
        { "<meta content=\"Daily Bugle\" property=\"og:title\">",
            "Daily Bugle",
            null,
            0,
            null},

        // Missing title
        { "<meta content=\"https://images.com/my-image.jpg\" property=\"og:image\">",
            null,
            null,
            0,
            "https://images.com/my-image.jpg"},

        // Has everything
        { "<meta property=\"og:title\" content =  \"Daily Bugle\">\n" +
          "<title>Daily Bugle HTML</title>\n" +
          "<meta property=\"og:image\" content=\"https://images.com/my-image.jpg\">\n" +
          "<link rel=\"icon\" href=\"https://images.com/favicon.png\" />",
            "Daily Bugle",
            null,
            0,
            "https://images.com/my-image.jpg"},

        // Fallback to HTML title
        { "<title>Daily Bugle HTML</title>\n" +
          "<meta property=\"og:image\" content=\"https://images.com/my-image.jpg\">\n" +
          "<link rel=\"icon\" href=\"https://images.com/favicon.png\" />",
            "Daily Bugle HTML",
            null,
            0,
            "https://images.com/my-image.jpg"},

        // Fallback to favicon
        { "<meta property=\"og:title\" content =  \"Daily Bugle\">\n" +
          "<title>Daily Bugle HTML</title>\n" +
          "<link rel=\"icon\" href=\"https://images.com/favicon.png\" />",
            "Daily Bugle",
            null,
            0,
            "https://images.com/favicon.png"},

        // Fallback to HTML title and favicon
        { "<title>Daily Bugle HTML</title>\n" +
          "<link rel=\"icon\" href=\"https://images.com/favicon.png\" />",
            "Daily Bugle HTML",
            null,
            0,
            "https://images.com/favicon.png"},

        // Different favicon formatting
        { "<title>Daily Bugle HTML</title>\n" +
          "<link rel=\"shortcut icon\" href=\"https://images.com/favicon.png\" />",
            "Daily Bugle HTML",
            null,
            0,
            "https://images.com/favicon.png"},

        // Date: published_time variation
        { "<meta content=\"1991-12-30T00:00:00+00:00\" property=\"article:published_time\">",
            null,
            null,
            694051200000L,
            null},

        // Date: Use modified_time if there's no published_time
        { "<meta content=\"1991-12-30T00:00:00+00:00\" property=\"og:modified_time\">",
            null,
            null,
            694051200000L,
            null},

        // Date: modified_time variation
        { "<meta content=\"1991-12-30T00:00:00+00:00\" property=\"article:modified_time\">",
            null,
            null,
            694051200000L,
            null},

        // Date: Prefer published_time
        { "<meta content=\"1991-12-31T00:00:00+00:00\" property=\"og:modified_time\">" +
          "<meta content=\"1991-12-30T00:00:00+00:00\" property=\"og:published_time\">",
            null,
            null,
            694051200000L,
            null},

        // Double encoded HTML
        { "<meta content=\"Daily Bugle\" property=\"og:title\">\n" +
          "<meta content=\"https://images.com/my-image.jpg\" property=\"og:image\">" +
          "<meta content=\"A newspaper&amp;#39;s\" property=\"og:description\">" +
          "<meta content=\"1991-12-30T00:00:00+00:00\" property=\"og:published_time\">",
          "Daily Bugle",
          "A newspaper's",
          694051200000L,
          "https://images.com/my-image.jpg"},

    });
  }

  public LinkPreviewUtilTest_parseOpenGraphFields(String html, String title, String description, long date, String imageUrl) {
    this.html        = html;
    this.title       = title;
    this.description = description;
    this.date        = date;
    this.imageUrl    = imageUrl;
  }

  @Test
  public void parseOpenGraphFields() {
    LinkPreviewUtil.OpenGraph openGraph = LinkPreviewUtil.parseOpenGraphFields(html);
    assertEquals(Optional.ofNullable(title), openGraph.getTitle());
    assertEquals(Optional.ofNullable(description), openGraph.getDescription());
    assertEquals(date, openGraph.getDate());
    assertEquals(Optional.ofNullable(imageUrl), openGraph.getImageUrl());
  }
}
