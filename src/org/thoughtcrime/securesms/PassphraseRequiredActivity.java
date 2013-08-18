package org.thoughtcrime.securesms;

import org.whispersystems.textsecure.crypto.MasterSecret;

public interface PassphraseRequiredActivity {
  public void onMasterSecretCleared();
  public void onNewMasterSecret(MasterSecret masterSecret);
}
