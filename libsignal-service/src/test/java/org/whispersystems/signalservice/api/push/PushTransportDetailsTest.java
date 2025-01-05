/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.push;

import org.junit.Test;
import org.whispersystems.signalservice.internal.push.PushTransportDetails;

import static org.junit.Assert.assertEquals;

public class PushTransportDetailsTest {

  @Test
  public void testV3Padding() {
    PushTransportDetails transportV3 = new PushTransportDetails();

    for (int i=0;i<159;i++) {
      byte[] message = new byte[i];
      assertEquals(159, transportV3.getPaddedMessageBody(message).length);
    }

    for (int i=159;i<319;i++) {
      byte[] message = new byte[i];
      assertEquals(319, transportV3.getPaddedMessageBody(message).length);
    }

    for (int i=319;i<479;i++) {
      byte[] message = new byte[i];
      assertEquals(479, transportV3.getPaddedMessageBody(message).length);
    }
  }
}
