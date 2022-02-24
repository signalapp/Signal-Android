package org.whispersystems.signalservice.api.messages;

import org.whispersystems.libsignal.util.guava.Optional;

public class SignalServicePreview {
  private final String                            url;
  private final String                            title;
  private final String                            description;
  private final long                              date;
  private final Optional<SignalServiceAttachment> image;

  public SignalServicePreview(String url, String title, String description, long date, Optional<SignalServiceAttachment> image) {
    this.url         = url;
    this.title       = title;
    this.description = description;
    this.date        = date;
    this.image       = image;
  }

  public String getUrl() {
    return url;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public long getDate() {
    return date;
  }

  public Optional<SignalServiceAttachment> getImage() {
    return image;
  }
}
