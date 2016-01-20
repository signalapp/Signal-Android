/*
 * Copyright (C) 2011 Whisper Systems
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

package org.privatechats.redphone.signaling.signals;

/**
 * Typically client endpoints are sending signals and receiving responses,
 * but in some cases the switch sends a signal and the client endpoint responds.
 *
 * This class encapsulates signals sent by the server.
 *
 * @author Moxie Marlinspike
 *
 */

public class ServerSignal {

  private final String verb;
  private final String target;
  private final byte[] body;

  private final long sessionId;

  public ServerSignal(String verb, String target, byte[] body) {
    this.verb   = verb;
    this.target = target;
    this.body   = body;

    if (target.startsWith("/session/")) {
      this.sessionId = Long.parseLong(target.substring("/session/".length()).trim());
    } else {
      this.sessionId = -1;
    }
  }

  public boolean isKeepAlive() {
    return verb.equals("GET") && target.startsWith("/keepalive");
  }

  public boolean isRinging(long sessionId) {
    return verb.equals("RING") && this.sessionId != -1 && this.sessionId == sessionId;
  }

  public boolean isHangup(long sessionId) {
    return verb.equals("DELETE") && this.sessionId != -1 && this.sessionId == sessionId;
  }

  public boolean isBusy(long sessionId) {
    return verb.equals("BUSY") && this.sessionId != -1 && this.sessionId == sessionId;
  }
}
