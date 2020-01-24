package org.whispersystems.signalservice.internal.contacts.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.internal.util.Hex;

public class KeyBackupRequest {

  @JsonProperty
  private byte[] requestId;

  @JsonProperty
  private byte[] iv;

  @JsonProperty
  private byte[] data;

  @JsonProperty
  private byte[] mac;

  @JsonProperty
  private String type;

  public KeyBackupRequest() {
  }

  public KeyBackupRequest(byte[] requestId, byte[] iv, byte[] data, byte[] mac, String type) {
    this.requestId = requestId;
    this.iv        = iv;
    this.data      = data;
    this.mac       = mac;
    this.type      = type;
  }

  public byte[] getRequestId() {
    return requestId;
  }

  public byte[] getIv() {
    return iv;
  }

  public byte[] getData() {
    return data;
  }

  public byte[] getMac() {
    return mac;
  }

  public String getType() {
    return type;
  }

  public String toString() {
    return "{ type:" + type + ", requestId: " + Hex.toString(requestId) + ", iv: " + Hex.toString(iv) + ", data: " + Hex.toString(data) + ", mac: " + Hex.toString(mac) + "}";
  }

}
