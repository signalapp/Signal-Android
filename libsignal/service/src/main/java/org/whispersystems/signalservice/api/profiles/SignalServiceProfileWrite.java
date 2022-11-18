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
  private byte[] paymentAddress;

  @JsonProperty
  private boolean avatar;

  @JsonProperty
  private byte[] commitment;

  @JsonCreator
  public SignalServiceProfileWrite(){
  }

  public SignalServiceProfileWrite(String version, byte[] name, byte[] about, byte[] aboutEmoji, byte[] paymentAddress, boolean avatar, byte[] commitment) {
    this.version        = version;
    this.name           = name;
    this.about          = about;
    this.aboutEmoji     = aboutEmoji;
    this.paymentAddress = paymentAddress;
    this.avatar         = avatar;
    this.commitment     = commitment;
  }

  public boolean hasAvatar() {
    return avatar;
  }
}
