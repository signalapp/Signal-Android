package org.whispersystems.signalservice.api.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.signal.libsignal.protocol.IdentityKey;
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.util.List;
import java.util.Map;

public final class ChangePhoneNumberRequest {
  @JsonProperty
  private String number;

  @JsonProperty
  private String code;

  @JsonProperty("reglock")
  private String registrationLock;

  @JsonProperty
  @JsonSerialize(using = JsonUtil.IdentityKeySerializer.class)
  @JsonDeserialize(using = JsonUtil.IdentityKeyDeserializer.class)
  private IdentityKey pniIdentityKey;

  @JsonProperty
  private List<OutgoingPushMessage> deviceMessages;

  @JsonProperty
  private Map<String, SignedPreKeyEntity> devicePniSignedPrekeys;

  @JsonProperty
  private Map<String, Integer> pniRegistrationIds;

  public ChangePhoneNumberRequest() {}

  public ChangePhoneNumberRequest(String number,
                                  String code,
                                  String registrationLock,
                                  IdentityKey pniIdentityKey,
                                  List<OutgoingPushMessage> deviceMessages,
                                  Map<String, SignedPreKeyEntity> devicePniSignedPrekeys,
                                  Map<String, Integer> pniRegistrationIds)
  {
    this.number                 = number;
    this.code                   = code;
    this.registrationLock       = registrationLock;
    this.pniIdentityKey         = pniIdentityKey;
    this.deviceMessages         = deviceMessages;
    this.devicePniSignedPrekeys = devicePniSignedPrekeys;
    this.pniRegistrationIds     = pniRegistrationIds;
  }

  public String getNumber() {
    return number;
  }

  public String getCode() {
    return code;
  }

  public String getRegistrationLock() {
    return registrationLock;
  }

  public IdentityKey getPniIdentityKey() {
    return pniIdentityKey;
  }

  public List<OutgoingPushMessage> getDeviceMessages() {
    return deviceMessages;
  }

  public Map<String, SignedPreKeyEntity> getDevicePniSignedPrekeys() {
    return devicePniSignedPrekeys;
  }

  public Map<String, Integer> getPniRegistrationIds() {
    return pniRegistrationIds;
  }
}
