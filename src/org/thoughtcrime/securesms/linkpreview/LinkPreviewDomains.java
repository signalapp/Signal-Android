package org.thoughtcrime.securesms.linkpreview;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LinkPreviewDomains {
  public static final String STICKERS = "signal.org";

  public static final Set<String> LINKS = new HashSet<>(Arrays.asList(
        // YouTube
      "youtube.com",
      "www.youtube.com",
      "m.youtube.com",
      "youtu.be",
      // Reddit
      "reddit.com",
      "www.reddit.com",
      "m.reddit.com",
      // Imgur
      "imgur.com",
      "www.imgur.com",
      "m.imgur.com",
      // Instagram
      "instagram.com",
      "www.instagram.com",
      "m.instagram.com",
      // Pinterest
      "pinterest.com",
      "www.pinterest.com",
      "pin.it",
      // Giphy
      "giphy.com",
      "media.giphy.com",
      "media1.giphy.com",
      "media2.giphy.com",
      "media3.giphy.com",
      "gph.is"
  ));

  public static final Set<String> IMAGES = new HashSet<>(Arrays.asList(
      "ytimg.com",
      "cdninstagram.com",
      "fbcdn.net",
      "redd.it",
      "imgur.com",
      "pinimg.com",
      "giphy.com"
  ));
}
