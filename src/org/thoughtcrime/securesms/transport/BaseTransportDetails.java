package org.thoughtcrime.securesms.transport;

import org.whispersystems.textsecure.crypto.TransportDetails;
import org.whispersystems.textsecure.util.Base64;

import java.io.IOException;

public class BaseTransportDetails implements TransportDetails {

  @Override
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

  @Override
  public byte[] getPaddedMessageBody(byte[] messageBody) {
    return messageBody;
  }

  @Override
  public byte[] encodeMessage(byte[] messageWithMac) {
    return Base64.encodeBytesWithoutPadding(messageWithMac).getBytes();
  }

  @Override
  public byte[] decodeMessage(byte[] encodedMessageBytes) throws IOException {
    return Base64.decodeWithoutPadding(new String(encodedMessageBytes));
  }
}
