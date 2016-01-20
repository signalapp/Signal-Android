package org.privatechats.securesms;

import android.support.annotation.NonNull;

import org.privatechats.securesms.crypto.MasterSecret;
import org.privatechats.securesms.database.model.ThreadRecord;

import java.util.Locale;
import java.util.Set;

public interface BindableConversationListItem extends Unbindable {

  public void bind(@NonNull MasterSecret masterSecret, @NonNull ThreadRecord thread,
                   @NonNull Locale locale, @NonNull Set<Long> selectedThreads, boolean batchMode);
}
