/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;


import org.signal.libsignal.protocol.logging.Log;

public class PushTransportDetails {

  private static final String TAG = PushTransportDetails.class.getSimpleName();
  private static final int PADDING_BLOCK_SIZE = 80;


  public byte[] getStrippedPaddingMessageBody(byte[] messageWithPadding) {
    int paddingStart = 0;

    for (int i=messageWithPadding.length-1;i>=0;i--) {
      if (messageWithPadding[i] == (byte)0x80) {
        paddingStart = i;
        break;
      } else if (messageWithPadding[i] != (byte)0x00) {
        Log.w(TAG, "Padding byte is malformed, returning unstripped padding.");
        return messageWithPadding;
      }
    }

    byte[] strippedMessage = new byte[paddingStart];
    System.arraycopy(messageWithPadding, 0, strippedMessage, 0, strippedMessage.length);

    return strippedMessage;
  }

  public byte[] getPaddedMessageBody(byte[] messageBody) {
    // NOTE: This is dumb.  We have our own padding scheme, but so does the cipher.
    // The +1 -1 here is to make sure the Cipher has room to add one padding byte,
    // otherwise it'll add a full 16 extra bytes.
    byte[] paddedMessage = new byte[getPaddedMessageLength(messageBody.length + 1) - 1];
    System.arraycopy(messageBody, 0, paddedMessage, 0, messageBody.length);
    paddedMessage[messageBody.length] = (byte)0x80;

    return paddedMessage;
  }

  private int getPaddedMessageLength(int messageLength) {
    int messageLengthWithTerminator = messageLength + 1;
    int messagePartCount            = messageLengthWithTerminator / PADDING_BLOCK_SIZE;

    if (messageLengthWithTerminator % PADDING_BLOCK_SIZE != 0) {
      messagePartCount++;
    }

    return messagePartCount * PADDING_BLOCK_SIZE;
  }
}
