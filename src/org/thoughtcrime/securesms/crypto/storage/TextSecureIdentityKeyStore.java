package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;

public class TextSecureIdentityKeyStore implements IdentityKeyStore {

  private final Context      context;
  private final MasterSecret masterSecret;

  public TextSecureIdentityKeyStore(Context context, MasterSecret masterSecret) {
    this.context      = context;
    this.masterSecret = masterSecret;
  }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    return IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);
  }

  @Override
  public int getLocalRegistrationId() {
    return TextSecurePreferences.getLocalRegistrationId(context);
  }

  @Override
  public void saveIdentity(long recipientId, IdentityKey identityKey) {
    DatabaseFactory.getIdentityDatabase(context).saveIdentity(masterSecret, recipientId, identityKey);
  }

  @Override
  public boolean isTrustedIdentity(long recipientId, IdentityKey identityKey) {
    return DatabaseFactory.getIdentityDatabase(context)
                          .isValidIdentity(masterSecret, recipientId, identityKey);
  }
}
