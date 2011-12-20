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
package org.thoughtcrime.securesms.sms;

import java.io.IOException;

import org.thoughtcrime.securesms.crypto.SessionCipher;
import org.thoughtcrime.securesms.crypto.TransportDetails;
import org.thoughtcrime.securesms.protocol.Prefix;
import org.thoughtcrime.securesms.protocol.WirePrefix;
import org.thoughtcrime.securesms.util.Base64;

import android.util.Log;

public class SmsTransportDetails implements TransportDetails {
	
  public static final int SMS_SIZE           = 160;
  public static final int MULTIPART_SMS_SIZE = 153;
	
  private static final int SINGLE_MESSAGE_MULTIPART_OVERHEAD      = 1;
  private static final int MULTI_MESSAGE_MULTIPART_OVERHEAD       = 3;
  private static final int FIRST_MULTI_MESSAGE_MULTIPART_OVERHEAD = 2;
	
  public static final int BASE_MAX_BYTES                     = Base64.getEncodedBytesForTarget(SMS_SIZE - WirePrefix.PREFIX_SIZE);
  public static final int SINGLE_MESSAGE_MAX_BYTES           = BASE_MAX_BYTES - SINGLE_MESSAGE_MULTIPART_OVERHEAD;
  public static final int MULTI_MESSAGE_MAX_BYTES            = BASE_MAX_BYTES - MULTI_MESSAGE_MULTIPART_OVERHEAD;
  public static final int FIRST_MULTI_MESSAGE_MAX_BYTES      = BASE_MAX_BYTES - FIRST_MULTI_MESSAGE_MULTIPART_OVERHEAD;
	
  public static final int ENCRYPTED_SINGLE_MESSAGE_BODY_MAX_SIZE = SINGLE_MESSAGE_MAX_BYTES - SessionCipher.ENCRYPTED_MESSAGE_OVERHEAD;
  //	private final int encryptedMessageOverhead;
  //	private final int encryptedSingleMessageBodyMaxSize;
	
  //	public SmsTransportDetails(int encryptedMessageOverhead) {
  //		this.encryptedMessageOverhead          = encryptedMessageOverhead;
  //		this.encryptedSingleMessageBodyMaxSize = SINGLE_MESSAGE_MAX_BYTES - encryptedMessageOverhead;
  //	}
	
  public byte[] encodeMessage(byte[] messageWithMac) {
    String encodedMessage = Base64.encodeBytesWithoutPadding(messageWithMac);
		
    Log.w("SmsTransportDetails", "Encoded Message Length: " + encodedMessage.length());
    return (Prefix.ASYMMETRIC_ENCRYPT + encodedMessage).getBytes();
  }
	
  public byte[] decodeMessage(byte[] encodedMessageBytes) throws IOException {
    String encodedMessage = new String(encodedMessageBytes);
    encodedMessage        = encodedMessage.substring(Prefix.ASYMMETRIC_ENCRYPT.length());
    return Base64.decodeWithoutPadding(encodedMessage);
  }
	
  public byte[] stripPaddedMessage(byte[] messageWithPadding) {
    int paddingBeginsIndex = 0;
		
    for (int i=1;i<messageWithPadding.length;i++) {
      if (messageWithPadding[i] == (byte)0x00) {
	paddingBeginsIndex = i;
	break;
      }				
    }
		
    if (paddingBeginsIndex == 0)
      return messageWithPadding;
		
    byte[] message = new byte[paddingBeginsIndex];
    System.arraycopy(messageWithPadding, 0, message, 0, message.length);
		
    return message;
  }
	
  public byte[] getPaddedMessageBody(byte[] messageBody) {
    int paddedBodySize;
		
    if (messageBody.length <= ENCRYPTED_SINGLE_MESSAGE_BODY_MAX_SIZE) {
      paddedBodySize = ENCRYPTED_SINGLE_MESSAGE_BODY_MAX_SIZE;
    } else {
      paddedBodySize = getMaxBodySizeForCurrentRecordCount(messageBody.length);
    }
		
    Log.w("SessionCipher", "Padding message body out to: " + paddedBodySize);
		
    byte[] paddedBody = new byte[paddedBodySize];
    //		byte[] bodyBytes  = messageBody.getBytes();
		
    assert(messageBody.length <= paddedBody.length);
		
    System.arraycopy(messageBody, 0, paddedBody, 0, messageBody.length);
		
    return paddedBody;
  }		
	
  public static final int getMessageCountForBytes(int bytes) {
    if (bytes <= SINGLE_MESSAGE_MAX_BYTES)
      return 1;
		
    bytes = Math.max(bytes - FIRST_MULTI_MESSAGE_MAX_BYTES, 0);
		
    int messageCount = 1 + (bytes / MULTI_MESSAGE_MAX_BYTES);
    int remainder    = bytes % MULTI_MESSAGE_MAX_BYTES;
		
    if (remainder > 0)
      messageCount++;
		
    return messageCount;
  }
	

	
  private int getMaxBodySizeForCurrentRecordCount(int bodyLength) {
    int messageRecordsForBody = getMessageCountForBytes(bodyLength + SessionCipher.ENCRYPTED_MESSAGE_OVERHEAD);
		
    if (messageRecordsForBody == 1)
      return ENCRYPTED_SINGLE_MESSAGE_BODY_MAX_SIZE;
    else
      return SmsTransportDetails.FIRST_MULTI_MESSAGE_MAX_BYTES                         + 
	(SmsTransportDetails.MULTI_MESSAGE_MAX_BYTES * (messageRecordsForBody-1)) - 
	SessionCipher.ENCRYPTED_MESSAGE_OVERHEAD;
  }	

	

}
