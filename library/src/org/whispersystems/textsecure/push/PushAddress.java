/**
 * Copyright (C) 2014 Open WhisperSystems
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
package org.whispersystems.textsecure.push;

import android.content.Context;

import org.whispersystems.textsecure.directory.Directory;
import org.whispersystems.textsecure.storage.RecipientDevice;

public class PushAddress extends RecipientDevice {

  private final String e164number;
  private final String relay;

  private PushAddress(long recipientId, String e164number, int deviceId, String relay) {
    super(recipientId, deviceId);
    this.e164number  = e164number;
    this.relay       = relay;
  }

  public String getNumber() {
    return e164number;
  }

  public String getRelay() {
    return relay;
  }

  public static PushAddress create(Context context, long recipientId, String e164number, int deviceId) {
    String relay = Directory.getInstance(context).getRelay(e164number);
    return new PushAddress(recipientId, e164number, deviceId, relay);
  }

}
