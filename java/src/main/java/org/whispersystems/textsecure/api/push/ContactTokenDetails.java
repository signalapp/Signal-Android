/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecure.api.push;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A class that represents a contact's registration state.
 */
public class ContactTokenDetails {

  @JsonProperty
  private String  token;

  @JsonProperty
  private String  relay;

  @JsonProperty
  private String  number;

  @JsonProperty
  private boolean voice;

  public ContactTokenDetails() {}

  /**
   * @return The "anonymized" token (truncated hash) that's transmitted to the server.
   */
  public String getToken() {
    return token;
  }

  /**
   * @return The federated server this contact is registered with, or null if on your server.
   */
  public String getRelay() {
    return relay;
  }

  /**
   * @return Whether this contact supports secure voice calls.
   */
  public boolean isVoice() {
    return voice;
  }

  public void setNumber(String number) {
    this.number = number;
  }

  /**
   * @return This contact's username (e164 formatted number).
   */
  public String getNumber() {
    return number;
  }
}
