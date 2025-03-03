package org.thoughtcrime.securesms.blocked;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;

import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.RecipientRecord;
import org.thoughtcrime.securesms.groups.GroupChangeBusyException;
import org.thoughtcrime.securesms.groups.GroupChangeFailedException;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

class BlockedUsersRepository {

  private static final String TAG = Log.tag(BlockedUsersRepository.class);

  private final Context context;

  BlockedUsersRepository(@NonNull Context context) {
    this.context = context;
  }

  void getBlocked(@NonNull Consumer<List<Recipient>> blockedUsers) {
    SignalExecutors.BOUNDED.execute(() -> {
      List<RecipientRecord> records    = SignalDatabase.recipients().getBlocked();
      List<Recipient>       recipients = records.stream()
                                                .map((record) -> Recipient.resolved(record.getId()))
                                                .collect(Collectors.toList());
      blockedUsers.accept(recipients);
    });
  }

  void block(@NonNull RecipientId recipientId, @NonNull Runnable success, @NonNull Runnable failure) {
    SignalExecutors.BOUNDED.execute(() -> {
      try {
        RecipientUtil.block(context, Recipient.resolved(recipientId));
        success.run();
      } catch (IOException | GroupChangeFailedException | GroupChangeBusyException e) {
        Log.w(TAG, "block: failed to block recipient: ", e);
        failure.run();
      }
    });
  }

  void createAndBlock(@NonNull String number, @NonNull Runnable success) {
    SignalExecutors.BOUNDED.execute(() -> {
      Recipient recipient = Recipient.external(number);
      if (recipient != null) {
        RecipientUtil.blockNonGroup(context, recipient);
      } else {
        Log.w(TAG, "Failed to create Recipient for number! Invalid input.");
      }
      success.run();
    });
  }

  void unblock(@NonNull RecipientId recipientId, @NonNull Runnable success) {
    SignalExecutors.BOUNDED.execute(() -> {
      RecipientUtil.unblock(Recipient.resolved(recipientId));
      success.run();
    });
  }
}
