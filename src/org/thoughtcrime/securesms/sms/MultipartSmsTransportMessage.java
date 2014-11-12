package org.thoughtcrime.securesms.sms;

import android.util.Log;

import org.thoughtcrime.securesms.protocol.EndSessionWirePrefix;
import org.thoughtcrime.securesms.protocol.KeyExchangeWirePrefix;
import org.thoughtcrime.securesms.protocol.PrekeyBundleWirePrefix;
import org.thoughtcrime.securesms.protocol.SecureMessageWirePrefix;
import org.thoughtcrime.securesms.protocol.WirePrefix;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.Conversions;

import java.io.IOException;
import java.util.ArrayList;

public class MultipartSmsTransportMessage {
  private static final String TAG = MultipartSmsTransportMessage.class.getName();

  private static final int MULTIPART_SUPPORTED_AFTER_VERSION = 1;

  public static final int SINGLE_MESSAGE_MULTIPART_OVERHEAD      = 1;
  public static final int MULTI_MESSAGE_MULTIPART_OVERHEAD       = 3;
  public static final int FIRST_MULTI_MESSAGE_MULTIPART_OVERHEAD = 2;

  public static final int WIRETYPE_SECURE      = 1;
  public static final int WIRETYPE_KEY         = 2;
  public static final int WIRETYPE_PREKEY      = 3;
  public static final int WIRETYPE_END_SESSION = 4;

  private static final int VERSION_OFFSET    = 0;
  private static final int MULTIPART_OFFSET  = 1;
  private static final int IDENTIFIER_OFFSET = 2;

  private final int                 wireType;
  private final byte[]              decodedMessage;
  private final IncomingTextMessage message;

  public MultipartSmsTransportMessage(IncomingTextMessage message) throws IOException {
    this.message         = message;
    this.decodedMessage  = Base64.decodeWithoutPadding(message.getMessageBody().substring(WirePrefix.PREFIX_SIZE));

    if      (WirePrefix.isEncryptedMessage(message.getMessageBody())) wireType = WIRETYPE_SECURE;
    else if (WirePrefix.isPreKeyBundle(message.getMessageBody()))     wireType = WIRETYPE_PREKEY;
    else if (WirePrefix.isEndSession(message.getMessageBody()))       wireType = WIRETYPE_END_SESSION;
    else                                                              wireType = WIRETYPE_KEY;

    Log.w(TAG, "Decoded message with version: " + getCurrentVersion());
  }

  public int getWireType() {
    return wireType;
  }

  public int getCurrentVersion() {
    return Conversions.highBitsToInt(decodedMessage[VERSION_OFFSET]);
  }

  public int getMultipartIndex() {
    return Conversions.highBitsToInt(decodedMessage[MULTIPART_OFFSET]);
  }

  public int getMultipartCount() {
    if (isDeprecatedTransport())
      return 1;

    return Conversions.lowBitsToInt(decodedMessage[MULTIPART_OFFSET]);
  }

  public int getIdentifier() {
    return decodedMessage[IDENTIFIER_OFFSET] & 0xFF;
  }

  public boolean isDeprecatedTransport() {
    return getCurrentVersion() < MULTIPART_SUPPORTED_AFTER_VERSION;
  }

  public boolean isInvalid() {
    return getMultipartIndex() >= getMultipartCount();
  }

  public boolean isSinglePart() {
    return getMultipartCount() == 1;
  }

  public byte[] getStrippedMessage() {
    if      (isDeprecatedTransport())  return getStrippedMessageForDeprecatedTransport();
    else if (getMultipartCount() == 1) return getStrippedMessageForSinglePart();
    else                               return getStrippedMessageForMultiPart();
  }

  /*
   * We're dealing with a message that isn't using the multipart transport.
   *
   */
  private byte[] getStrippedMessageForDeprecatedTransport() {
    return decodedMessage;
  }

  /*
   * We're dealing with a transport message that is of the format:
   * Version         (1 byte)
   * Index_And_Count (1 byte)
   * Message         (remainder)
   *
   * The version byte was stolen off the message, so we strip Index_And_Count byte out,
   * put the version byte back on the front of the message, and return.
   */
  private byte[] getStrippedMessageForSinglePart() {
    byte[] stripped = new byte[decodedMessage.length - 1];
    System.arraycopy(decodedMessage, 1, stripped, 0, decodedMessage.length - 1);
    stripped[0] = decodedMessage[VERSION_OFFSET];

    return stripped;
  }

  /*
   * We're dealing with a transport message that is of the format:
   *
   * Version         (1 byte)
   * Index_And_Count (1 byte)
   * Identifier      (1 byte)
   * Message         (remainder)
   *
   * The version byte was stolen off the first byte of the message, but only for the first fragment
   * of the message.  So for the first fragment we strip off everything and put the version byte
   * back on.  For the remaining fragments, we just strip everything.
   */

  private byte[] getStrippedMessageForMultiPart() {
    byte[] strippedMessage    = new byte[decodedMessage.length - (getMultipartIndex() == 0 ? 2 : 3)];

    int copyDestinationIndex  = 0;
    int copyDestinationLength = strippedMessage.length;

    if (getMultipartIndex() == 0) {
      strippedMessage[0] = decodedMessage[0];
      copyDestinationIndex++;
      copyDestinationLength--;
    }

    System.arraycopy(decodedMessage, 3, strippedMessage, copyDestinationIndex, copyDestinationLength);
    return strippedMessage;

  }

  public String getKey() {
    return message.getSender() + getIdentifier();
  }

  public IncomingTextMessage getBaseMessage() {
    return message;
  }

  public static ArrayList<String> getEncoded(OutgoingTextMessage message, byte identifier)
  {
    try {
      byte[] decoded = Base64.decodeWithoutPadding(message.getMessageBody());
      int count      = new SmsTransportDetails().getMessageCountForBytes(decoded.length);

      WirePrefix prefix;

      if      (message.isKeyExchange())  prefix = new KeyExchangeWirePrefix();
      else if (message.isPreKeyBundle()) prefix = new PrekeyBundleWirePrefix();
      else if (message.isEndSession())   prefix = new EndSessionWirePrefix();
      else                               prefix = new SecureMessageWirePrefix();

      if (count == 1) return getSingleEncoded(decoded, prefix);
      else            return getMultiEncoded(decoded, prefix, count, identifier);

    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static ArrayList<String> getSingleEncoded(byte[] decoded, WirePrefix prefix) {
    ArrayList<String> list            = new ArrayList<String>(1);
    byte[] messageWithMultipartHeader = new byte[decoded.length + 1];
    System.arraycopy(decoded, 0, messageWithMultipartHeader, 1, decoded.length);

    messageWithMultipartHeader[VERSION_OFFSET]   = decoded[VERSION_OFFSET];
    messageWithMultipartHeader[MULTIPART_OFFSET] = Conversions.intsToByteHighAndLow(0, 1);

    String encodedMessage = Base64.encodeBytesWithoutPadding(messageWithMultipartHeader);

    list.add(prefix.calculatePrefix(encodedMessage) + encodedMessage);

    Log.w(TAG, "Complete fragment size: " + list.get(list.size()-1).length());

    return list;
  }

  private static ArrayList<String> getMultiEncoded(byte[] decoded, WirePrefix prefix,
                                                   int segmentCount, byte id)
  {
    ArrayList<String> list            = new ArrayList<String>(segmentCount);
    byte versionByte                  = decoded[VERSION_OFFSET];
    int messageOffset                 = 1;
    int segmentIndex                  = 0;

    while (messageOffset < decoded.length-1) {
      int segmentSize = Math.min(SmsTransportDetails.BASE_MAX_BYTES, decoded.length-messageOffset+3);

      byte[] segment             = new byte[segmentSize];
      segment[VERSION_OFFSET]    = versionByte;
      segment[MULTIPART_OFFSET]  = Conversions.intsToByteHighAndLow(segmentIndex++, segmentCount);
      segment[IDENTIFIER_OFFSET] = id;

      Log.w(TAG, "Fragment: (" + segmentIndex + "/" + segmentCount +") -- ID: " + id);

      System.arraycopy(decoded, messageOffset, segment, 3, segmentSize-3);
      messageOffset  += segmentSize-3;

      String encodedSegment = Base64.encodeBytesWithoutPadding(segment);
      list.add(prefix.calculatePrefix(encodedSegment) + encodedSegment);

      Log.w(TAG, "Complete fragment size: " + list.get(list.size()-1).length());
    }

    return list;
  }

}
