/** 
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2014 Open Whisper Systems
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
package org.thoughtcrime.securesms.mms;

import org.thoughtcrime.securesms.util.Base64;

import java.io.IOException;


public class TextTransport {

  public byte[] getDecodedMessage(byte[] encodedMessageBytes) throws IOException {
    return Base64.decode(encodedMessageBytes);
  }

  public byte[] getEncodedMessage(byte[] messageWithMac) {
    return Base64.encodeBytes(messageWithMac).getBytes();
  }
}
