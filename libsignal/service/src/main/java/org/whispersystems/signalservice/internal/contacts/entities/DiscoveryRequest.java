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

import org.whispersystems.signalservice.internal.util.Hex;


public class DiscoveryRequest {

  @JsonProperty
  private int addressCount;

  @JsonProperty
  private byte[] requestId;

  @JsonProperty
  private byte[] iv;

  @JsonProperty
  private byte[] data;

  @JsonProperty
  private byte[] mac;

  public DiscoveryRequest() {

  }

  public DiscoveryRequest(int addressCount, byte[] requestId, byte[] iv, byte[] data, byte[] mac) {
    this.addressCount = addressCount;
    this.requestId    = requestId;
    this.iv           = iv;
    this.data         = data;
    this.mac          = mac;
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

  public int getAddressCount() {
    return addressCount;
  }

  public String toString() {
    return "{ addressCount: " + addressCount + ", ticket: " + Hex.toString(requestId) + ", iv: " + Hex.toString(iv) + ", data: " + Hex.toString(data) + ", mac: " + Hex.toString(mac) + "}";
  }

}
