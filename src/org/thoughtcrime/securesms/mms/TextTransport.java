/** 
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
package org.thoughtcrime.securesms.mms;

import java.io.IOException;

import org.thoughtcrime.securesms.crypto.TransportDetails;
import org.thoughtcrime.securesms.util.Base64;

public class TextTransport implements TransportDetails {

  public byte[] decodeMessage(byte[] encodedMessageBytes) throws IOException {
    return Base64.decode(encodedMessageBytes);
  }

  public byte[] encodeMessage(byte[] messageWithMac) {
    return Base64.encodeBytes(messageWithMac).getBytes();
  }

  public byte[] getPaddedMessageBody(byte[] messageBody) {
    return messageBody;
  }

  public byte[] stripPaddedMessage(byte[] messageWithPadding) {
    return messageWithPadding;
  }

}
