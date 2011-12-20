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
package org.thoughtcrime.securesms.protocol;

import java.nio.ByteBuffer;

import org.thoughtcrime.securesms.crypto.InvalidKeyException;
import org.thoughtcrime.securesms.crypto.InvalidMessageException;
import org.thoughtcrime.securesms.crypto.PublicKey;
import org.thoughtcrime.securesms.util.Conversions;

import android.util.Log;

/**
 * Parses and serializes the encrypted message format.
 * 
 * @author Moxie Marlinspike
 */

public class Message {
	
  public static final int SUPPORTED_VERSION       = 1;
	
  private static final int VERSION_LENGTH         = 1;
  private static final int SENDER_KEY_ID_LENGTH   = 3;
  private static final int RECEIVER_KEY_ID_LENGTH = 3;
  private static final int NEXT_KEY_LENGTH        = PublicKey.KEY_SIZE;
  private static final int COUNTER_LENGTH         = 3;
  public static final int HEADER_LENGTH          =  VERSION_LENGTH + SENDER_KEY_ID_LENGTH + RECEIVER_KEY_ID_LENGTH + COUNTER_LENGTH + NEXT_KEY_LENGTH;
	
  private static final int VERSION_OFFSET         = 0;
  private static final int SENDER_KEY_ID_OFFSET   = VERSION_OFFSET + VERSION_LENGTH;
  private static final int RECEIVER_KEY_ID_OFFSET = SENDER_KEY_ID_OFFSET + SENDER_KEY_ID_LENGTH;
  private static final int NEXT_KEY_OFFSET        = RECEIVER_KEY_ID_OFFSET + RECEIVER_KEY_ID_LENGTH;
  private static final int COUNTER_OFFSET         = NEXT_KEY_OFFSET + NEXT_KEY_LENGTH;
  private static final int TEXT_OFFSET            = COUNTER_OFFSET + COUNTER_LENGTH;

  private int senderKeyId;
  private int receiverKeyId;
  private int counter;
  private int messageVersion;
  private int supportedVersion;
  private byte[] message;
	
  private PublicKey nextKey;
	
  public Message(int senderKeyId, int receiverKeyId, PublicKey nextKey, int counter, byte[] message, int messageVersion, int supportedVersion) {
    this.senderKeyId      = senderKeyId;
    this.receiverKeyId    = receiverKeyId;
    this.nextKey          = nextKey;
    this.counter          = counter;
    this.message          = message;
    this.messageVersion   = messageVersion;
    this.supportedVersion = supportedVersion;
  }
	
  public Message(byte[] messageBytes) throws InvalidMessageException {
    try {
      if (messageBytes.length <= HEADER_LENGTH)
        throw new InvalidMessageException("Message is shorter than headers.");
			
      this.messageVersion   = Conversions.highBitsToInt(messageBytes[VERSION_OFFSET]);
      this.supportedVersion = Conversions.lowBitsToInt(messageBytes[VERSION_OFFSET]);
			
      Log.w("Message", "Message Version: " + messageVersion);
      Log.w("Message", "Supported Version: " + supportedVersion);
			
      if (messageVersion > SUPPORTED_VERSION)
        throw new InvalidMessageException("Message protocol version not supported: " + messageVersion);
			
      this.senderKeyId      = Conversions.byteArrayToMedium(messageBytes, SENDER_KEY_ID_OFFSET);
      this.receiverKeyId    = Conversions.byteArrayToMedium(messageBytes, RECEIVER_KEY_ID_OFFSET);
      this.counter          = Conversions.byteArrayToMedium(messageBytes, COUNTER_OFFSET);
			
      Log.w("Message", "Parsed current version: " + messageVersion + " supported version: " + supportedVersion);
			
      byte[] nextKeyBytes = new byte[NEXT_KEY_LENGTH];
      byte[] textBytes    = new byte[messageBytes.length - HEADER_LENGTH];
			
      System.arraycopy(messageBytes, NEXT_KEY_OFFSET, nextKeyBytes, 0, nextKeyBytes.length);
      System.arraycopy(messageBytes, TEXT_OFFSET, textBytes, 0, textBytes.length);
			
      Log.w("Message", "Pulling next key out of message...");
      this.nextKey       = new PublicKey(nextKeyBytes);
      this.message       = textBytes;
    } catch (InvalidKeyException ike) {
      throw new AssertionError(ike);
    }
  }
	
  public byte[] serialize() {
    ByteBuffer buffer  = ByteBuffer.allocate(HEADER_LENGTH + message.length);
		
    Log.w("Message", "Constructing Message Version: (" + messageVersion + "," + supportedVersion + ")");

    byte   versionByte        = Conversions.intsToByteHighAndLow(messageVersion, supportedVersion);
    byte[] senderKeyIdBytes   = Conversions.mediumToByteArray(senderKeyId);
    byte[] receiverKeyIdBytes = Conversions.mediumToByteArray(receiverKeyId);
    Log.w("Message", "Serializing next key into message...");
    byte[] nextKeyBytes       = nextKey.serialize();
    byte[] counterBytes       = Conversions.mediumToByteArray(counter);
		
    buffer.put(versionByte);
    buffer.put(senderKeyIdBytes);
    buffer.put(receiverKeyIdBytes);
    buffer.put(nextKeyBytes);
    buffer.put(counterBytes);
    buffer.put(message);
		
    return buffer.array();
  }
	
  public int getHighestMutuallySupportedVersion() {
    return Math.min(SUPPORTED_VERSION, this.supportedVersion);
  }
	
  public int getSenderKeyId() {
    return this.senderKeyId;
  }
	
  public int getReceiverKeyId() {
    return this.receiverKeyId;
  }
	
  public PublicKey getNextKey() {
    return this.nextKey;
  }
	
  public int getCounter() {
    return this.counter;
  }
	
  public byte[] getMessageText() {
    return this.message;
  }
	
}
