package org.thoughtcrime.securesms.groups.ui;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.recipients.Recipient;

public interface RecipientLongClickListener {
  boolean onLongClick(@NonNull Recipient recipient);
}
