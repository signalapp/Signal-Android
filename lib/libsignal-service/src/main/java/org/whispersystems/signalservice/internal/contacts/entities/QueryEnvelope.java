package org.whispersystems.signalservice.internal.contacts.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

public class QueryEnvelope {

  @JsonProperty
  private byte[] requestId;

  @JsonProperty
  private byte[] iv;

  @JsonProperty
  private byte[] data;

  @JsonProperty
  private byte[] mac;

  public QueryEnvelope() { }

  public QueryEnvelope(byte[] requestId, byte[] iv, byte[] data, byte[] mac) {
    this.requestId = requestId;
    this.iv        = iv;
    this.data      = data;
    this.mac       = mac;
  }
}
