package org.whispersystems.signalservice.api.profiles;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SignalServiceProfileWrite {

  @JsonProperty
  private String version;

  @JsonProperty
  private byte[] name;

  @JsonProperty
  private byte[] about;

  @JsonProperty
  private byte[] aboutEmoji;

  @JsonProperty
  private boolean avatar;

  @JsonProperty
  private byte[] commitment;

  @JsonCreator
  public SignalServiceProfileWrite(){
  }

  public SignalServiceProfileWrite(String version, byte[] name, byte[] about, byte[] aboutEmoji, boolean avatar, byte[] commitment) {
    this.version    = version;
    this.name       = name;
    this.about      = about;
    this.aboutEmoji = aboutEmoji;
    this.avatar     = avatar;
    this.commitment = commitment;
  }

  public boolean hasAvatar() {
    return avatar;
  }
}
