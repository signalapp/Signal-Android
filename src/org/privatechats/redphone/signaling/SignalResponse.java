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

package org.privatechats.redphone.signaling;

import java.util.Map;

/**
 * A helper class that encapsulates the status, headers, and body
 * of a signal response.
 *
 * @author Moxie Marlinspike
 *
 */

public class SignalResponse {

  private final int statusCode;
  private final Map<String, String> headers;
  private final byte[] body;

  public SignalResponse(int statusCode, Map<String, String> headers, byte[] body) {
    this.statusCode = statusCode;
    this.headers    = headers;
    this.body       = body;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public byte[] getBody() {
    return body;
  }

}
