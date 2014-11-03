package org.thoughtcrime.securesms;

import org.thoughtcrime.securesms.crypto.MasterSecret;

public interface PassphraseRequiredActivity {
  public void onMasterSecretCleared();
  public void onNewMasterSecret(MasterSecret masterSecret);
}
