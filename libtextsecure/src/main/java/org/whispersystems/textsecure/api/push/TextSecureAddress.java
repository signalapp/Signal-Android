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

/**
 * A class representing a message destination or origin.
 */
public class TextSecureAddress {

  public static final int DEFAULT_DEVICE_ID = 1;

  private final long   recipientId;
  private final String e164number;
  private final String relay;

  /**
   * Construct a PushAddress.
   *
   * @param recipientId The axolotl recipient ID of this destination.
   * @param e164number The TextSecure username of this destination (eg e164 representation of a phone number).
   * @param relay The TextSecure federated server this user is registered with (if not your own server).
   */
  public TextSecureAddress(long recipientId, String e164number, String relay) {
    this.recipientId = recipientId;
    this.e164number  = e164number;
    this.relay       = relay;
  }

  public String getNumber() {
    return e164number;
  }

  public String getRelay() {
    return relay;
  }

  public long getRecipientId() {
    return recipientId;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof TextSecureAddress)) return false;

    TextSecureAddress that = (TextSecureAddress)other;

    return this.recipientId == that.recipientId &&
           equals(this.e164number, that.e164number) &&
           equals(this.relay, that.relay);
  }

  @Override
  public int hashCode() {
    int hashCode = (int)this.recipientId;

    if (this.e164number != null) hashCode ^= this.e164number.hashCode();
    if (this.relay != null)      hashCode ^= this.relay.hashCode();

    return hashCode;
  }

  private boolean equals(String one, String two) {
    if (one == null) return two == null;
    return one.equals(two);
  }
}
