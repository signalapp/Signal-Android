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

import java.util.List;
import java.util.Map;

public class DiscoveryRequest {

  @JsonProperty
  private int addressCount;

  @JsonProperty
  private byte[] commitment;

  @JsonProperty
  private byte[] iv;

  @JsonProperty
  private byte[] data;

  @JsonProperty
  private byte[] mac;

  @JsonProperty
  private Map<String, QueryEnvelope> envelopes;

  public DiscoveryRequest() { }

  public DiscoveryRequest(int addressCount, byte[] commitment, byte[] iv, byte[] data, byte[] mac, Map<String, QueryEnvelope> envelopes) {
    this.addressCount = addressCount;
    this.commitment   = commitment;
    this.iv           = iv;
    this.data         = data;
    this.mac          = mac;
    this.envelopes    = envelopes;
  }

  public byte[] getCommitment() {
    return commitment;
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

  public int getAddressCount() {
    return addressCount;
  }

  @Override
  public String toString() {
    return "{ addressCount: " + addressCount + ", envelopes: " + envelopes.size() + " }";
  }
}
