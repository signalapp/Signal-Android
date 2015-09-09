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
 * A signal that requests a verification code via SMS
 * in order to initiate the account creation process.
 *
 * @author Moxie Marlinspike
 *
 */

public class CreateAccountSignal extends Signal {

  private final boolean voice;

  public CreateAccountSignal(String localNumber, String password, boolean voice) {
    super(localNumber, password, -1);
    this.voice = voice;
  }

  @Override
  protected String getMethod() {
    return "GET";
  }

  @Override
  protected String getLocation() {
    if (!voice) return "/users/verification/sms";
    else        return "/users/verification/voice";
  }

  @Override
  protected String getBody() {
    return null;
  }
}
