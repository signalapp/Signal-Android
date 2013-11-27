package org.thoughtcrime.securesms.push;

import android.content.Context;

import org.thoughtcrime.securesms.R;
import org.whispersystems.textsecure.push.PushServiceSocket;

import java.io.InputStream;

public class TextSecurePushTrustStore implements PushServiceSocket.TrustStore {

  private final Context context;

  public TextSecurePushTrustStore(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public InputStream getKeyStoreInputStream() {
    return context.getResources().openRawResource(R.raw.whisper);
  }

  @Override
  public String getKeyStorePassword() {
    return "whisper";
  }
}
