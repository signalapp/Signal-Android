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

public class PushAddress {

  public static final int DEFAULT_DEVICE_ID = 1;

  private final long   recipientId;
  private final String e164number;
  private final int    deviceId;
  private final String relay;

  public PushAddress(long recipientId, String e164number, int deviceId, String relay) {
    this.recipientId = recipientId;
    this.e164number  = e164number;
    this.deviceId    = deviceId;
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

  public int getDeviceId() {
    return deviceId;
  }
}
