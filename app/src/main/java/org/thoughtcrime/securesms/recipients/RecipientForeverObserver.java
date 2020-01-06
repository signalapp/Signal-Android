package org.thoughtcrime.securesms.recipients;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

public interface RecipientForeverObserver {
  @MainThread
  void onRecipientChanged(@NonNull Recipient recipient);
}
