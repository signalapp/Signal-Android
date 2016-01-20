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
 * A signal which indicates that the endpoint has registered
 * for C2DM updates at the specified registration ID.  The
 * server can now "push" signals to this endpoint via C2DM rather
 * than SMS.
 *
 * @author Moxie Marlinspike
 *
 */

public class C2DMRegistrationSignal extends Signal {

  private final String registrationId;

  public C2DMRegistrationSignal(String localNumber, String password, String registrationId) {
    super(localNumber, password, -1);
    this.registrationId = registrationId;
  }

  @Override
  protected String getMethod() {
    return "PUT";
  }

  @Override
  protected String getLocation() {
    return "/c2dm/" + registrationId;
  }

  @Override
  protected String getBody() {
    return null;
  }

}
