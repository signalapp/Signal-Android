package org.thoughtcrime.securesms.registration.testdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.thoughtcrime.securesms.testutil.HexDeserializer;

public class PinSanitationVector {

  @JsonProperty("name")
  private String name;

  @JsonProperty("pin")
  private String pin;

  @JsonProperty("bytes")
  @JsonDeserialize(using = HexDeserializer.class)
  private byte[] bytes;

  public String getName() {
    return name;
  }

  public String getPin() {
    return pin;
  }

  public byte[] getBytes() {
    return bytes;
  }
}