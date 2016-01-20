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
 * A signal which indicates that the endpoint is no
 * longer registered with C2DM.
 *
 * @author Moxie Marlinspike
 *
 */


public class C2DMUnregistrationSignal extends Signal {

  public C2DMUnregistrationSignal(String localNumber, String password) {
    super(localNumber, password, -1);
  }

  @Override
  protected String getMethod() {
    return "DELETE";
  }

  @Override
  protected String getLocation() {
    return "/c2dm";
  }

  @Override
  protected String getBody() {
    return null;
  }
}
