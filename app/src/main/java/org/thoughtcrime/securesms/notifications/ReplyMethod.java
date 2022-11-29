package org.thoughtcrime.securesms.notifications;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;

public enum ReplyMethod {

  GroupMessage,
  SecureMessage,
  UnsecuredSmsMessage;

  public static @NonNull ReplyMethod forRecipient(Context context, Recipient recipient) {
    if (recipient.isGroup()) {
      return ReplyMethod.GroupMessage;
    } else if (SignalStore.account().isRegistered() && recipient.getRegistered() == RecipientTable.RegisteredState.REGISTERED && !recipient.isForceSmsSelection()) {
      return ReplyMethod.SecureMessage;
    } else {
      return ReplyMethod.UnsecuredSmsMessage;
    }
  }
}
