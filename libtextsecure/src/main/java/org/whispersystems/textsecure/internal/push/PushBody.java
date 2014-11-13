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
package org.whispersystems.textsecure.internal.push;

public class PushBody {

  private final int    type;
  private final int    remoteRegistrationId;
  private final byte[] body;

  public PushBody(int type, int remoteRegistrationId, byte[] body) {
    this.type                 = type;
    this.remoteRegistrationId = remoteRegistrationId;
    this.body                 = body;
  }

  public int getType() {
    return type;
  }

  public byte[] getBody() {
    return body;
  }

  public int getRemoteRegistrationId() {
    return remoteRegistrationId;
  }
}
