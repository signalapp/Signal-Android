package org.thoughtcrime.securesms.notifications;

import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public enum ReplyMethod {

  GroupMessage,
  SecureMessage;

  public static @NonNull ReplyMethod forRecipient(Context context, Recipient recipient) {
    if (recipient.isGroupRecipient()) {
      return ReplyMethod.GroupMessage;
    }
    return ReplyMethod.SecureMessage;
  }
}
