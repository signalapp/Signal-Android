package org.privatechats.redphone.signaling;

import android.content.Context;

import org.privatechats.securesms.R;
import org.whispersystems.textsecure.api.push.TrustStore;

import java.io.InputStream;

public class RedPhoneTrustStore implements TrustStore {

  private final Context context;

  public RedPhoneTrustStore(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public InputStream getKeyStoreInputStream() {
    return context.getResources().openRawResource(R.raw.redphone);
  }

  @Override
  public String getKeyStorePassword() {
    return "whisper";
  }
}
