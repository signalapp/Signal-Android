package org.thoughtcrime.securesms.database.loaders;


import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.AbstractCursorLoader;

public class ThreadMediaLoader extends AbstractCursorLoader {

  private final Address      address;
  private final MasterSecret masterSecret;

  public ThreadMediaLoader(@NonNull Context context, @NonNull MasterSecret masterSecret, @NonNull Address address) {
    super(context);
    this.masterSecret = masterSecret;
    this.address      = address;
  }

  @Override
  public Cursor getCursor() {
    long threadId = DatabaseFactory.getThreadDatabase(getContext()).getThreadIdFor(Recipient.from(getContext(), address, true));
    return DatabaseFactory.getMediaDatabase(getContext()).getMediaForThread(threadId);
  }

  public Address getAddress() {
    return address;
  }

  public MasterSecret getMasterSecret() {
    return masterSecret;
  }
}
