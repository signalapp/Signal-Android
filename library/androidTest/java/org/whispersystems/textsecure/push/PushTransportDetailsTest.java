package org.whispersystems.textsecure.push;

import android.test.AndroidTestCase;
import android.util.Base64;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

public class PushTransportDetailsTest extends AndroidTestCase {

  private final PushTransportDetails transportV2 = new PushTransportDetails(2);
  private final PushTransportDetails transportV3 = new PushTransportDetails(3);

  public void testV3Padding() {
    for (int i=0;i<159;i++) {
      byte[] message = new byte[i];
      assertEquals(transportV3.getPaddedMessageBody(message).length, 159);
    }

    for (int i=159;i<319;i++) {
      byte[] message = new byte[i];
      assertEquals(transportV3.getPaddedMessageBody(message).length, 319);
    }

    for (int i=319;i<479;i++) {
      byte[] message = new byte[i];
      assertEquals(transportV3.getPaddedMessageBody(message).length, 479);
    }
  }

  public void testV2Padding() {
    for (int i=0;i<480;i++) {
      byte[] message = new byte[i];
      assertTrue(transportV2.getPaddedMessageBody(message).length == message.length);
    }
  }

  public void testV3Encoding() throws NoSuchAlgorithmException {
    byte[] message = new byte[501];
    SecureRandom.getInstance("SHA1PRNG").nextBytes(message);

    byte[] padded = transportV3.getEncodedMessage(message);

    assertTrue(Arrays.equals(padded, message));
  }

  public void testV2Encoding() throws NoSuchAlgorithmException {
    byte[] message = new byte[501];
    SecureRandom.getInstance("SHA1PRNG").nextBytes(message);

    byte[] padded = transportV2.getEncodedMessage(message);

    assertTrue(Arrays.equals(padded, message));
  }

}
