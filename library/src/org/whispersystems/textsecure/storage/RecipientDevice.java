/**
 * Copyright (C) 2011-2014 Whisper Systems
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
package org.whispersystems.textsecure.storage;

public class RecipientDevice {

  public static final int DEFAULT_DEVICE_ID = 1;

  private final long recipientId;
  private final int  deviceId;

  public RecipientDevice(long recipientId, int deviceId) {
    this.recipientId = recipientId;
    this.deviceId    = deviceId;
  }

  public long getRecipientId() {
    return recipientId;
  }

  public int getDeviceId() {
    return deviceId;
  }

  public CanonicalRecipient getRecipient() {
    return new CanonicalRecipient() {
      @Override
      public long getRecipientId() {
        return recipientId;
      }
    };
  }
}
