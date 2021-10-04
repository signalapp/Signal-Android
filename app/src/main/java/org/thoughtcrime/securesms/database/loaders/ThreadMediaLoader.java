package org.thoughtcrime.securesms.database.loaders;


import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import org.session.libsession.utilities.Address;
import org.session.libsession.utilities.recipients.Recipient;
import org.thoughtcrime.securesms.dependencies.DatabaseComponent;
import org.thoughtcrime.securesms.util.AbstractCursorLoader;

public class ThreadMediaLoader extends AbstractCursorLoader {

  private final Address address;
  private final boolean gallery;

  public ThreadMediaLoader(@NonNull Context context, @NonNull Address address, boolean gallery) {
    super(context);
    this.address = address;
    this.gallery = gallery;
  }

  @Override
  public Cursor getCursor() {
    long threadId = DatabaseComponent.get(context).threadDatabase().getOrCreateThreadIdFor(Recipient.from(getContext(), address, true));

    if (gallery) return DatabaseComponent.get(context).mediaDatabase().getGalleryMediaForThread(threadId);
    else         return DatabaseComponent.get(context).mediaDatabase().getDocumentMediaForThread(threadId);
  }

  public Address getAddress() {
    return address;
  }

}
