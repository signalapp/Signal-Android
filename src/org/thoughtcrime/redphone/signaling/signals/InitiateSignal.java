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

//import org.thoughtcrime.redphone.util.PhoneNumberFormatter;

import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

/**
 * A signal which initiates a call with the specified remote number.
 *
 * @author Moxie Marlinspike
 *
 */

public class InitiateSignal extends Signal {

  private final String remoteNumber;

  public InitiateSignal(String localNumber, String password, long counter, String remoteNumber) {
    super(localNumber, password, counter);
    try {
      this.remoteNumber = PhoneNumberFormatter.formatNumber(remoteNumber, localNumber);
    } catch (InvalidNumberException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  protected String getMethod() {
    return "GET";
  }

  @Override
  protected String getLocation() {
    return "/session/1/" + remoteNumber;
  }

  @Override
  protected String getBody() {
    return null;
  }
}
