package org.whispersystems.textsecure.push;

import org.whispersystems.textsecure.crypto.TransportDetails;

import java.io.IOException;

public class RawTransportDetails implements TransportDetails {
  @Override
  public byte[] getStrippedPaddingMessageBody(byte[] messageWithPadding) {
    return messageWithPadding;
  }

  @Override
  public byte[] getPaddedMessageBody(byte[] messageBody) {
    return messageBody;
  }

  @Override
  public byte[] getEncodedMessage(byte[] messageWithMac) {
    return messageWithMac;
  }

  @Override
  public byte[] getDecodedMessage(byte[] encodedMessageBytes) throws IOException {
    return encodedMessageBytes;
  }
}
