package org.thoughtcrime.securesms.linkpreview;

public class Link {

  private final String url;
  private final int    position;

  public Link(String url, int position) {
    this.url      = url;
    this.position = position;
  }

  public String getUrl() {
    return url;
  }

  public int getPosition() {
    return position;
  }
}
