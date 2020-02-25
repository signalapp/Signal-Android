package org.thoughtcrime.securesms.linkpreview;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LinkPreviewDomains {
  public static final String STICKERS = "signal.org";

  public static final Set<String> LINKS = new HashSet<>(Arrays.asList(
      "youtube.com",
      "www.youtube.com",
      "m.youtube.com",
      "youtu.be",
      "reddit.com",
      "www.reddit.com",
      "m.reddit.com",
      "imgur.com",
      "www.imgur.com",
      "m.imgur.com",
      "instagram.com",
      "www.instagram.com",
      "m.instagram.com",
      "pinterest.com",
      "www.pinterest.com",
      "pin.it"
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
