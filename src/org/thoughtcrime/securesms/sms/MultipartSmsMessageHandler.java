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

import android.util.Log;

import org.thoughtcrime.securesms.util.Base64;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class MultipartSmsMessageHandler {

  private static final String TAG = MultipartSmsMessageHandler.class.getSimpleName();

  private final HashMap<String, MultipartSmsTransportMessageFragments> partialMessages = new HashMap<>();

  private IncomingTextMessage processMultipartMessage(MultipartSmsTransportMessage message) {
    Log.w(TAG, "Processing multipart message...");
    Log.w(TAG, "Multipart Count: " + message.getMultipartCount());
    Log.w(TAG, "Multipart ID: " + message.getIdentifier());
    Log.w(TAG, "Multipart Key: " + message.getKey());
    MultipartSmsTransportMessageFragments container = partialMessages.get(message.getKey());

    Log.w(TAG, "Found multipart container: " + container);

    if (container == null || container.getSize() != message.getMultipartCount() || container.isExpired()) {
      Log.w(TAG, "Constructing new container...");
      container = new MultipartSmsTransportMessageFragments(message.getMultipartCount());
      partialMessages.put(message.getKey(), container);
    }

    container.add(message);

    Log.w(TAG, "Filled buffer at index: " + message.getMultipartIndex());

    if (!container.isComplete())
      return null;

    partialMessages.remove(message.getKey());
    String strippedMessage = Base64.encodeBytesWithoutPadding(container.getJoined());

    if (message.getWireType() == MultipartSmsTransportMessage.WIRETYPE_KEY) {
      return new IncomingKeyExchangeMessage(message.getBaseMessage(), strippedMessage);
    } else if (message.getWireType() == MultipartSmsTransportMessage.WIRETYPE_PREKEY) {
      return new IncomingPreKeyBundleMessage(message.getBaseMessage(), strippedMessage);
    } else {
      return new IncomingEncryptedMessage(message.getBaseMessage(), strippedMessage);
    }
  }

  private IncomingTextMessage processSinglePartMessage(MultipartSmsTransportMessage message) {
    Log.w(TAG, "Processing single part message...");
    String strippedMessage = Base64.encodeBytesWithoutPadding(message.getStrippedMessage());

    if (message.getWireType() == MultipartSmsTransportMessage.WIRETYPE_KEY) {
      return new IncomingKeyExchangeMessage(message.getBaseMessage(), strippedMessage);
    } else if (message.getWireType() == MultipartSmsTransportMessage.WIRETYPE_PREKEY) {
      return new IncomingPreKeyBundleMessage(message.getBaseMessage(), strippedMessage);
    } else if (message.getWireType() == MultipartSmsTransportMessage.WIRETYPE_END_SESSION) {
      return new IncomingEndSessionMessage(message.getBaseMessage(), strippedMessage);
    } else {
      return new IncomingEncryptedMessage(message.getBaseMessage(), strippedMessage);
    }
  }

  public synchronized IncomingTextMessage processPotentialMultipartMessage(IncomingTextMessage message) {
    try {
      MultipartSmsTransportMessage transportMessage = new MultipartSmsTransportMessage(message);

      if      (transportMessage.isInvalid())    return message;
      else if (transportMessage.isSinglePart()) return processSinglePartMessage(transportMessage);
      else                                      return processMultipartMessage(transportMessage);
    } catch (IOException e) {
      Log.w(TAG, e);
      return message;
    }
  }

  public synchronized ArrayList<String> divideMessage(OutgoingTextMessage message) {
    String number     = message.getRecipients().getPrimaryRecipient().getNumber();
    byte   identifier = MultipartSmsIdentifier.getInstance().getIdForRecipient(number);
    return MultipartSmsTransportMessage.getEncoded(message, identifier);
  }
}
