package org.whispersystems.signalservice.push;

import junit.framework.TestCase;

import org.whispersystems.signalservice.internal.push.PushTransportDetails;

public class PushTransportDetailsTest extends TestCase {

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
}
