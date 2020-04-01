package org.thoughtcrime.securesms;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Lifecycle;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

public final class BlockUnblockDialog {

  private BlockUnblockDialog() {
  }

  public static void handleBlock(@NonNull Context context,
                                 @NonNull Lifecycle lifecycle,
                                 @NonNull RecipientId recipientId)
  {
    SimpleTask.run(
      lifecycle,
      () -> {
        AlertDialog.Builder builder  = new AlertDialog.Builder(context);
        Recipient           resolved = Recipient.resolved(recipientId);

        if (resolved.isGroup()) {
          if (DatabaseFactory.getGroupDatabase(context).isActive(resolved.requireGroupId())) {
            builder.setTitle(R.string.RecipientPreferenceActivity_block_and_leave_group);
          } else {
            builder.setTitle(R.string.RecipientPreferenceActivity_block_group);
          }
          builder.setMessage(R.string.RecipientPreferenceActivity_block_and_leave_group_description);
        } else {
          builder.setTitle(R.string.RecipientPreferenceActivity_block_this_contact_question)
                 .setMessage(R.string.RecipientPreferenceActivity_you_will_no_longer_receive_messages_and_calls_from_this_contact);
        }

        return builder.setCancelable(true)
                      .setNegativeButton(android.R.string.cancel, null)
                      .setPositiveButton(R.string.RecipientPreferenceActivity_block, (dialog, which) -> setBlocked(context, resolved, true));
      },
      AlertDialog.Builder::show);
  }

  public static void handleUnblock(@NonNull Context context,
                                   @NonNull Lifecycle lifecycle,
                                   @NonNull RecipientId recipientId,
                                   @Nullable Runnable postUnblock)
  {
    SimpleTask.run(
      lifecycle,
      () -> {
        AlertDialog.Builder builder  = new AlertDialog.Builder(context);
        Recipient           resolved = Recipient.resolved(recipientId);

        if (resolved.isGroup()) {
          builder.setTitle(R.string.RecipientPreferenceActivity_unblock_this_group_question)
                 .setMessage(R.string.RecipientPreferenceActivity_unblock_this_group_description);
        } else {
          builder.setTitle(R.string.RecipientPreferenceActivity_unblock_this_contact_question)
                 .setMessage(R.string.RecipientPreferenceActivity_you_will_once_again_be_able_to_receive_messages_and_calls_from_this_contact);
        }

        return builder.setCancelable(true)
                      .setNegativeButton(android.R.string.cancel, null)
                      .setPositiveButton(R.string.RecipientPreferenceActivity_unblock, (dialog, which) -> {
                        setBlocked(context, resolved, false);
                        if (postUnblock != null) postUnblock.run();
                      });
      },
      AlertDialog.Builder::show);
  }

  private static void setBlocked(@NonNull final Context context, final Recipient recipient, final boolean blocked) {
    SignalExecutors.BOUNDED.execute(() -> {
      if (blocked) {
        RecipientUtil.block(context, recipient);
      } else {
        RecipientUtil.unblock(context, recipient);
      }
    });
  }
}
