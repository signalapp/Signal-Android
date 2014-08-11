package org.thoughtcrime.securesms.service;

import android.content.Intent;
import android.test.InstrumentationTestCase;

import static org.fest.assertions.api.Assertions.*;

public class MmsReceiverTest extends InstrumentationTestCase {

  private MmsReceiver mmsReceiver;

  public void setUp() throws Exception {
    super.setUp();
    mmsReceiver = new MmsReceiver(getInstrumentation().getContext());
  }

  public void tearDown() throws Exception {

  }

  public void testProcessMalformedData() throws Exception {
    Intent intent = new Intent();
    intent.setAction(SendReceiveService.RECEIVE_MMS_ACTION);
    intent.putExtra("data", new byte[]{0x00});
    mmsReceiver.process(null, intent);
  }

}
