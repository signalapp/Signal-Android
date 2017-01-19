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

package org.thoughtcrime.redphone.signaling;

import android.util.Log;

import org.thoughtcrime.redphone.util.LineReader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A helper class that reads signal bytes off the wire.
 *
 * @author Moxie Marlinspike
 *
 */

public class SignalReader {

  protected final LineReader lineReader;

  public SignalReader(LineReader lineReader) {
    this.lineReader = lineReader;
  }

  public String[] readSignalRequest() throws SignalingException, IOException {
    String requestLine = lineReader.readLine();

    if (requestLine == null || requestLine.length() == 0)
      throw new SignalingException("Server failure.");

    String[] request = requestLine.split(" ");

    if (request == null || request.length != 3)
      throw new SignalingException("Got strange request: " + requestLine);

    return request;
  }

  public Map<String, String> readSignalHeaders() throws IOException {
    Map<String, String> headers = new HashMap<String, String>();
    String header;

    while ((header = lineReader.readLine()).length() != 0) {
      String[] split = header.split(":");

      if (split == null || split.length != 2)
        continue;

      headers.put(split[0].trim(), split[1].trim());
    }

    return headers;
  }

  public byte[] readSignalBody(Map<String, String> headers) throws SignalingException, IOException {
    if (headers.containsKey("Content-Length")) {
      try {
        String contentLengthString = headers.get("Content-Length");
        int contentLength          = Integer.parseInt(contentLengthString);

        if (contentLength != 0) {
          return lineReader.readFully(contentLength);
        }
      } catch (NumberFormatException nfe) {
        Log.w("SignalingSocket", nfe);
      }
    }

    return new byte[0];
  }

}
