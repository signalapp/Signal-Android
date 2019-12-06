package org.whispersystems.signalservice.internal.contacts.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.internal.util.Hex;

public class KeyBackupResponse {

  @JsonProperty
  private byte[] iv;

  @JsonProperty
  private byte[] data;

  @JsonProperty
  private byte[] mac;

  public KeyBackupResponse() {}

  public KeyBackupResponse(byte[] iv, byte[] data, byte[] mac) {
    this.iv   = iv;
    this.data = data;
    this.mac  = mac;
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

  public String toString() {
    return "{iv: " + (iv == null ? null : Hex.toString(iv)) + ", data: " + (data == null ? null: Hex.toString(data)) + ", mac: " + (mac == null ? null : Hex.toString(mac)) + "}";
  }
}
