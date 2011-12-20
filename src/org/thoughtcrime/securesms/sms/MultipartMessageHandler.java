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
import java.util.ArrayList;
import java.util.HashMap;

import org.thoughtcrime.securesms.protocol.Message;
import org.thoughtcrime.securesms.protocol.Prefix;
import org.thoughtcrime.securesms.protocol.WirePrefix;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.Conversions;
import org.thoughtcrime.securesms.util.Hex;

import android.telephony.SmsManager;
import android.util.Log;

public class MultipartMessageHandler {
	
  private static final int VERSION_OFFSET    = 0;
  private static final int MULTIPART_OFFSET  = 1;
  private static final int IDENTIFIER_OFFSET = 2;

  private static final int MULTIPART_SUPPORTED_AFTER_VERSION = 1;
	
  private final HashMap<String, byte[][]> partialMessages = new HashMap<String, byte[][]>();
  private final HashMap<String, Integer>  idMap           = new HashMap<String, Integer>();
	
  private String spliceMessage(String prefix, byte[][] messageParts) {
    Log.w("MultipartMessageHandler", "Have complete message fragments, splicing...");
    int totalMessageLength = 0;
		
    for (int i=0;i<messageParts.length;i++) {
      totalMessageLength += messageParts[i].length;
    }
		
    byte[] totalMessage    = new byte[totalMessageLength];
    int totalMessageOffset = 0;
		
    for (int i=0;i<messageParts.length;i++) {
      System.arraycopy(messageParts[i], 0, totalMessage, totalMessageOffset, messageParts[i].length);
      totalMessageOffset += messageParts[i].length;
    }

    return prefix + Base64.encodeBytesWithoutPadding(totalMessage);
  }
	
  private boolean isComplete(byte[][] partialMessages) {
    for (int i=0;i<partialMessages.length;i++)
      if (partialMessages[i] == null) return false;
		
    Log.w("MultipartMessageHandler", "Buffer complete!");
		
    return true;
  }
	
  private byte[][] findOrAllocateMultipartBuffer(String sender, int identifier, int count) {
    String key = sender + identifier;
		
    Log.w("MultipartMessageHandler", "Getting multipart buffer...");
		
    if (partialMessages.containsKey(key)) {
      Log.w("MultipartMessageHandler", "Returning existing multipart buffer...");
      return partialMessages.get(key);
    } else {
      Log.w("MultipartMessageHandler", "Creating new multipart buffer: " + count);
      byte[][] multipartBuffer = new byte[count][];
      partialMessages.put(key, multipartBuffer);
      return multipartBuffer;
    }
  }

  private byte[] stripMultipartTransportLayer(int index, byte[] decodedMessage) {
    byte[] strippedMessage    = new byte[decodedMessage.length - (index == 0 ? 2 : 3)];
    int copyDestinationIndex  = 0;
    int copyDestinationLength = strippedMessage.length;
		
    if (index == 0) {
      strippedMessage[0] = decodedMessage[0];
      copyDestinationIndex++;
      copyDestinationLength--;
    }			
			
    System.arraycopy(decodedMessage, 3, strippedMessage, copyDestinationIndex, copyDestinationLength);
    return strippedMessage;
  }
	
  private String processMultipartMessage(String prefix, int index, int count, String sender, int identifier, byte[] decodedMessage) {
    Log.w("MultipartMessageHandler", "Processing multipart message...");
    decodedMessage        = stripMultipartTransportLayer(index, decodedMessage);
    byte[][] messageParts = findOrAllocateMultipartBuffer(sender, identifier, count);
    messageParts[index]   = decodedMessage;
		
    Log.w("MultipartMessageHandler", "Filled buffer at index: " + index);
		
    if (!isComplete(messageParts))
      return null;
		
    partialMessages.remove(sender+identifier);
    return spliceMessage(prefix, messageParts);
  }
	
  private String processSinglePartMessage(String prefix, byte[] decodedMessage) {
    Log.w("MultipartMessageHandler", "Processing single part message...");
    decodedMessage[MULTIPART_OFFSET] = decodedMessage[VERSION_OFFSET];
    return prefix + Base64.encodeBytesWithoutPadding(decodedMessage, 1, decodedMessage.length-1);
  }
	
  public String processPotentialMultipartMessage(String prefix, String sender, String message) {
    try {
      byte[] decodedMessage  = Base64.decodeWithoutPadding(message);
      int currentVersion     = Conversions.highBitsToInt(decodedMessage[VERSION_OFFSET]);
			
      Log.w("MultipartMessageHandler", "Decoded message with version: " + currentVersion);
      Log.w("MultipartMessageHandler", "Decoded message: " + Hex.toString(decodedMessage));
			
      if (currentVersion < MULTIPART_SUPPORTED_AFTER_VERSION)
	throw new AssertionError("Caller should have checked this.");
			
      int multipartIndex     = Conversions.highBitsToInt(decodedMessage[MULTIPART_OFFSET]);
      int multipartCount     = Conversions.lowBitsToInt(decodedMessage[MULTIPART_OFFSET]);
      int identifier         = decodedMessage[IDENTIFIER_OFFSET] & 0xFF;
			
      Log.w("MultipartMessageHandler", "Multipart Info: (" + multipartIndex + "/" + multipartCount + ") ID: " + identifier); 
			
      if (multipartIndex >= multipartCount)
	return message;
			
      if (multipartCount == 1) return processSinglePartMessage(prefix, decodedMessage);
      else                     return processMultipartMessage(prefix, multipartIndex, multipartCount, sender, identifier, decodedMessage);
			
    } catch (IOException e) {
      return message;
    }
  }
	
  private ArrayList<String> buildSingleMessage(byte[] decodedMessage, WirePrefix prefix) {
    Log.w("MultipartMessageHandler", "Adding transport info to single-part message...");
		
    ArrayList<String> list            = new ArrayList<String>();
    byte[] messageWithMultipartHeader = new byte[decodedMessage.length + 1];
    System.arraycopy(decodedMessage, 0, messageWithMultipartHeader, 1, decodedMessage.length);		

    messageWithMultipartHeader[0]     = decodedMessage[0];
    messageWithMultipartHeader[1]     = Conversions.intsToByteHighAndLow(0, 1);
    String encodedMessage             = Base64.encodeBytesWithoutPadding(messageWithMultipartHeader);
		
    list.add(prefix.calculatePrefix(encodedMessage) + encodedMessage);
    Log.w("MultipartMessageHandler", "Complete fragment size: " + list.get(list.size()-1).length());

    return list;
  }
	
  private byte getIdForRecipient(String recipient) {
    Integer currentId;
		
    if (idMap.containsKey(recipient)) {
      currentId = idMap.get(recipient);
      idMap.remove(recipient);
    } else {
      currentId = new Integer(0);
    }

    byte id  = currentId.byteValue();
    idMap.put(recipient, new Integer((currentId.intValue() + 1) % 255));
		
    return id;
  }
	
  private ArrayList<String> buildMultipartMessage(String recipient, byte[] decodedMessage, WirePrefix prefix) {
    Log.w("MultipartMessageHandler", "Building multipart message...");
		
    ArrayList<String> list            = new ArrayList<String>();
    byte versionByte                  = decodedMessage[0];
    int messageOffset                 = 1;
    int segmentIndex                  = 0;
    int segmentCount                  = SmsTransportDetails.getMessageCountForBytes(decodedMessage.length);
    byte id                           = getIdForRecipient(recipient);
		
    while (messageOffset < decodedMessage.length-1) {
      int segmentSize = Math.min(SmsTransportDetails.BASE_MAX_BYTES, decodedMessage.length-messageOffset+3);
      byte[] segment  = new byte[segmentSize];
      segment[0]      = versionByte;
      segment[1]      = Conversions.intsToByteHighAndLow(segmentIndex++, segmentCount);
      segment[2]      = id;

      Log.w("MultipartMessageHandler", "Fragment: (" + segmentIndex + "/" + segmentCount +") -- ID: " + id);
			
      System.arraycopy(decodedMessage, messageOffset, segment, 3, segmentSize-3);
      messageOffset  += segmentSize-3;
			
      String encodedSegment = Base64.encodeBytesWithoutPadding(segment);
      list.add(prefix.calculatePrefix(encodedSegment) + encodedSegment);
			
      Log.w("MultipartMessageHandler", "Complete fragment size: " + list.get(list.size()-1).length());
    }
		
    return list;
  }
	
  public boolean isManualTransport(String message) {
    try {
      byte[] decodedMessage = Base64.decodeWithoutPadding(message);			
      return Conversions.highBitsToInt(decodedMessage[0]) >= MULTIPART_SUPPORTED_AFTER_VERSION;
    } catch (IOException ioe) {
      throw new AssertionError(ioe);
    }
  }
	
  public ArrayList<String> divideMessage(String recipient, String message, WirePrefix prefix) {
    try {			
      byte[] decodedMessage = Base64.decodeWithoutPadding(message);

      if (decodedMessage.length <= SmsTransportDetails.SINGLE_MESSAGE_MAX_BYTES) 
	return buildSingleMessage(decodedMessage, prefix);
      else                			  
	return buildMultipartMessage(recipient, decodedMessage, prefix);
    } catch	(IOException ioe) {
      throw new AssertionError(ioe);
    }
  }
}
