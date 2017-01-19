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

import org.thoughtcrime.redphone.util.LineReader;

import java.io.IOException;

/**
 * A helper class that reads signal response bytes off the wire.
 *
 * @author Moxie Marlinspike
 *
 */

public class SignalResponseReader extends SignalReader {

  public SignalResponseReader(LineReader lineReader) {
    super(lineReader);
  }

  public int readSignalResponseCode() throws SignalingException, IOException {
    String responseLine = lineReader.readLine();

    if (responseLine == null || responseLine.length() == 0)
      throw new SignalingException("Failed to read response.");

    String[] responseLineParts = responseLine.split(" ", 3);

    if (responseLineParts == null || responseLineParts.length != 3)
      throw new SignalingException("Failed to parse response line: " + responseLine);

    try {
      return Integer.parseInt(responseLineParts[1]);
    } catch (NumberFormatException nfe) {
      throw new SignalingException("Failed to parse status code from: " + responseLine);
    }
  }

}
