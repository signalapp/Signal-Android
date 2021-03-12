package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;

import org.session.libsignal.libsignal.IdentityKeyPair;
import org.session.libsignal.libsignal.state.IdentityKeyStore;
import org.session.libsession.utilities.IdentityKeyUtil;

public class SignalProtocolStoreImpl implements IdentityKeyStore {

  private final Context context;

  public SignalProtocolStoreImpl(Context context) {
    this.context = context;
  }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    return IdentityKeyUtil.getIdentityKeyPair(context);
  }
}
