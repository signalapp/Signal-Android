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

package org.thoughtcrime.redphone.signaling.signals;

/**
 * A signal which indicates that the endpoint is hanging
 * up the call associated with the specified session ID.
 *
 * @author Moxie Marlinspike
 *
 */

public class HangupSignal extends Signal {

  private final long sessionId;

  public HangupSignal(String localNumber, String password, long counter, long sessionId) {
    super(localNumber, password, counter);
    this.sessionId = sessionId;
  }

  @Override
  protected String getMethod() {
    return "DELETE";
  }

  @Override
  protected String getLocation() {
    return "/session/" + sessionId;
  }

  @Override
  protected String getBody() {
    return null;
  }

}
