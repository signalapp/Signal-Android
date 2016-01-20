package org.privatechats.securesms;

import android.support.annotation.NonNull;

import org.privatechats.securesms.crypto.MasterSecret;
import org.privatechats.securesms.database.model.MessageRecord;
import org.privatechats.securesms.recipients.Recipients;

import java.util.Locale;
import java.util.Set;

public interface BindableConversationItem extends Unbindable {
  void bind(@NonNull MasterSecret masterSecret,
            @NonNull MessageRecord messageRecord,
            @NonNull Locale locale,
            @NonNull Set<MessageRecord> batchSelected,
            @NonNull Recipients recipients);
}
