package org.thoughtcrime.securesms.registration.v2.testdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.thoughtcrime.securesms.testutil.HexDeserializer;

public final class KbsTestVector {

  @JsonProperty("backup_id")
  @JsonDeserialize(using = HexDeserializer.class)
  private byte[] backupId;

  @JsonProperty("argon2_hash")
  @JsonDeserialize(using = HexDeserializer.class)
  private byte[] argon2Hash;

  @JsonProperty("pin")
  private String pin;

  @JsonProperty("registration_lock")
  private String registrationLock;

  @JsonProperty("master_key")
  @JsonDeserialize(using = HexDeserializer.class)
  private byte[] masterKey;

  @JsonProperty("kbs_access_key")
  @JsonDeserialize(using = HexDeserializer.class)
  private byte[] kbsAccessKey;

  @JsonProperty("iv_and_cipher")
  @JsonDeserialize(using = HexDeserializer.class)
  private byte[] ivAndCipher;

  public byte[] getBackupId() {
    return backupId;
  }

  public byte[] getArgon2Hash() {
    return argon2Hash;
  }

  public String getPin() {
    return pin;
  }

  public String getRegistrationLock() {
    return registrationLock;
  }

  public byte[] getMasterKey() {
    return masterKey;
  }

  public byte[] getKbsAccessKey() {
    return kbsAccessKey;
  }

  public byte[] getIvAndCipher() {
    return ivAndCipher;
  }
}
