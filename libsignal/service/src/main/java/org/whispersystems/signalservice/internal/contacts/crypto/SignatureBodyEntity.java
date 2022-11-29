package org.whispersystems.signalservice.internal.contacts.crypto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SignatureBodyEntity {

  @JsonProperty
  private byte[] isvEnclaveQuoteBody;

  @JsonProperty
  private String isvEnclaveQuoteStatus;

  @JsonProperty
  private Long version;

  @JsonProperty
  private String timestamp;

  @JsonProperty
  private String advisoryURL;

  @JsonProperty
  private String[] advisoryIDs;

  public byte[] getIsvEnclaveQuoteBody() {
    return isvEnclaveQuoteBody;
  }

  public String getIsvEnclaveQuoteStatus() {
    return isvEnclaveQuoteStatus;
  }

  public String getAdvisoryUrl() {
    return advisoryURL;
  }

  public String[] getAdvisoryIds() {
    return advisoryIDs;
  }

  public Long getVersion() {
    return version;
  }

  public String getTimestamp() {
    return timestamp;
  }
}
