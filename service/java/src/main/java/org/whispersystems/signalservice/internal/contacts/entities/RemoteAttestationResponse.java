/*
 * Copyright (C) 2017 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.signalservice.internal.contacts.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RemoteAttestationResponse {

  @JsonProperty
  private byte[] serverEphemeralPublic;

  @JsonProperty
  private byte[] serverStaticPublic;

  @JsonProperty
  private byte[] quote;

  @JsonProperty
  private byte[] iv;

  @JsonProperty
  private byte[] ciphertext;

  @JsonProperty
  private byte[] tag;

  @JsonProperty
  private String signature;

  @JsonProperty
  private String certificates;

  @JsonProperty
  private String signatureBody;

  public RemoteAttestationResponse(byte[] serverEphemeralPublic, byte[] serverStaticPublic,
                                   byte[] iv, byte[] ciphertext, byte[] tag,
                                   byte[] quote,  String signature, String certificates, String signatureBody)
  {
    this.serverEphemeralPublic = serverEphemeralPublic;
    this.serverStaticPublic    = serverStaticPublic;
    this.iv                    = iv;
    this.ciphertext            = ciphertext;
    this.tag                   = tag;
    this.quote                 = quote;
    this.signature             = signature;
    this.certificates          = certificates;
    this.signatureBody         = signatureBody;
  }

  public RemoteAttestationResponse() {}

  public byte[] getServerEphemeralPublic() {
    return serverEphemeralPublic;
  }

  public byte[] getServerStaticPublic() {
    return serverStaticPublic;
  }

  public byte[] getQuote() {
    return quote;
  }

  public byte[] getIv() {
    return iv;
  }

  public byte[] getCiphertext() {
    return ciphertext;
  }

  public byte[] getTag() {
    return tag;
  }

  public String getSignature() {
    return signature;
  }

  public String getCertificates() {
    return certificates;
  }

  public String getSignatureBody() {
    return signatureBody;
  }

}
