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

import org.thoughtcrime.redphone.crypto.Otp;
import org.thoughtcrime.securesms.util.Base64;

/**
 * Base class for constructing a signal.
 *
 * @author Moxie Marlinspike
 *
 */

public abstract class Signal {

  private final String localNumber;
  private final String password;
  private final long counter;

  public Signal(String localNumber, String password, long counter) {
    this.localNumber = localNumber;
    this.password    = password;
    this.counter     = counter;
  }

  public String serialize() {
    StringBuffer sb = new StringBuffer();

    buildRequest(sb, getMethod(), getLocation());
    buildAuthorization(sb, password, counter);
    buildBody(sb, getBody());

    return sb.toString();
  }

  private void buildBody(StringBuffer sb, String body) {
    if (body != null && body.length() > 0) {
      sb.append("Content-Length: ");
      sb.append(body.length());
      sb.append("\r\n");
    }

    sb.append("\r\n");

    if (body!= null && body.length() > 0) {
      sb.append(body);
    }
  }

  private void buildAuthorization(StringBuffer sb, String password, long counter) {
    if (password != null && counter == -1) {
      sb.append("Authorization: Basic ");
      sb.append(Base64.encodeBytes((localNumber + ":" + password).getBytes()));
      sb.append("\r\n");
    } else if (password != null) {
      sb.append("Authorization: OTP ");
      sb.append(Base64.encodeBytes((localNumber + ":" +
          Otp.calculateOtp(password, counter) + ":" +
          counter).getBytes()));
      sb.append("\r\n");
    }
  }

  private void buildRequest(StringBuffer sb, String method, String location) {
    sb.append(getMethod());
    sb.append(" ");
    sb.append(getLocation());
    sb.append(" HTTP/1.0\r\n");
  }

  protected abstract String getMethod();
  protected abstract String getLocation();
  protected abstract String getBody();

}
