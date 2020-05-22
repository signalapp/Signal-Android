package org.thoughtcrime.securesms.groups.ui;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.recipients.Recipient;

public interface RecipientClickListener {
  void onClick(@NonNull Recipient recipient);
}
