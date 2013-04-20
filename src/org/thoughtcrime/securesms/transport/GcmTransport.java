package org.thoughtcrime.securesms.transport;

import android.content.Context;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;

import java.io.IOException;

public class GcmTransport {

  private final Context context;
  private final MasterSecret masterSecret;

  public GcmTransport(Context context, MasterSecret masterSecret) {
    this.context      = context.getApplicationContext();
    this.masterSecret = masterSecret;
  }

  public void deliver(SmsMessageRecord message) throws IOException {

  }

}
