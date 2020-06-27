package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Lifecycle;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

/**
 * This should be used whenever we want to prompt the user to block/unblock a recipient.
 */
public final class BlockUnblockDialog {

  private BlockUnblockDialog() { }

  public static void showBlockFor(@NonNull Context context,
                                  @NonNull Lifecycle lifecycle,
                                  @NonNull Recipient recipient,
                                  @NonNull Runnable onBlock)
  {
    SimpleTask.run(lifecycle,
                   () -> buildBlockFor(context, recipient, onBlock, null),
                   AlertDialog.Builder::show);
  }

  public static void showBlockAndDeleteFor(@NonNull Context context,
                                           @NonNull Lifecycle lifecycle,
                                           @NonNull Recipient recipient,
                                           @NonNull Runnable onBlock,
                                           @NonNull Runnable onBlockAndDelete)
  {
    SimpleTask.run(lifecycle,
        () -> buildBlockFor(context, recipient, onBlock, onBlockAndDelete),
        AlertDialog.Builder::show);
  }

  public static void showUnblockFor(@NonNull Context context,
                                    @NonNull Lifecycle lifecycle,
                                    @NonNull Recipient recipient,
                                    @NonNull Runnable onUnblock)
  {
    SimpleTask.run(lifecycle,
                   () -> buildUnblockFor(context, recipient, onUnblock),
                   AlertDialog.Builder::show);
  }

  @WorkerThread
  private static AlertDialog.Builder buildBlockFor(@NonNull Context context,
                                                   @NonNull Recipient recipient,
                                                   @NonNull Runnable onBlock,
                                                   @Nullable Runnable onBlockAndDelete)
  {
    recipient = recipient.resolve();

    AlertDialog.Builder builder   = new AlertDialog.Builder(context);
    Resources           resources = context.getResources();

    if (recipient.isGroup()) {
      if (DatabaseFactory.getGroupDatabase(context).isActive(recipient.requireGroupId())) {
        builder.setTitle(resources.getString(R.string.BlockUnblockDialog_block_and_leave_s, recipient.getDisplayName(context)));
        builder.setMessage(R.string.BlockUnblockDialog_you_will_no_longer_receive_messages_or_updates);
        builder.setPositiveButton(R.string.BlockUnblockDialog_block_and_leave, ((dialog, which) -> onBlock.run()));
        builder.setNegativeButton(android.R.string.cancel, null);
      } else {
        builder.setTitle(resources.getString(R.string.BlockUnblockDialog_block_s, recipient.getDisplayName(context)));
        builder.setMessage(R.string.BlockUnblockDialog_group_members_wont_be_able_to_add_you);
        builder.setPositiveButton(R.string.RecipientPreferenceActivity_block, ((dialog, which) -> onBlock.run()));
        builder.setNegativeButton(android.R.string.cancel, null);
      }
    } else {
      builder.setTitle(resources.getString(R.string.BlockUnblockDialog_block_s, recipient.getDisplayName(context)));
      builder.setMessage(R.string.BlockUnblockDialog_blocked_people_wont_be_able_to_call_you_or_send_you_messages);

      if (onBlockAndDelete != null) {
        builder.setNeutralButton(android.R.string.cancel, null);
        builder.setPositiveButton(R.string.BlockUnblockDialog_block_and_delete, (d, w) -> onBlockAndDelete.run());
        builder.setNegativeButton(R.string.BlockUnblockDialog_block, (d, w) -> onBlock.run());
      } else {
        builder.setPositiveButton(R.string.BlockUnblockDialog_block, ((dialog, which) -> onBlock.run()));
        builder.setNegativeButton(android.R.string.cancel, null);
      }
    }

    return builder;
  }

  @WorkerThread
  private static AlertDialog.Builder buildUnblockFor(@NonNull Context context,
                                                     @NonNull Recipient recipient,
                                                     @NonNull Runnable onUnblock)
  {
    recipient = recipient.resolve();

    AlertDialog.Builder builder   = new AlertDialog.Builder(context);
    Resources           resources = context.getResources();

    if (recipient.isGroup()) {
      if (DatabaseFactory.getGroupDatabase(context).isActive(recipient.requireGroupId())) {
        builder.setTitle(resources.getString(R.string.BlockUnblockDialog_unblock_s, recipient.getDisplayName(context)));
        builder.setMessage(R.string.BlockUnblockDialog_group_members_will_be_able_to_add_you);
        builder.setPositiveButton(R.string.RecipientPreferenceActivity_unblock, ((dialog, which) -> onUnblock.run()));
        builder.setNegativeButton(android.R.string.cancel, null);
      } else {
        builder.setTitle(resources.getString(R.string.BlockUnblockDialog_unblock_s, recipient.getDisplayName(context)));
        builder.setMessage(R.string.BlockUnblockDialog_group_members_will_be_able_to_add_you);
        builder.setPositiveButton(R.string.RecipientPreferenceActivity_unblock, ((dialog, which) -> onUnblock.run()));
        builder.setNegativeButton(android.R.string.cancel, null);
      }
    } else {
      builder.setTitle(resources.getString(R.string.BlockUnblockDialog_unblock_s, recipient.getDisplayName(context)));
      builder.setMessage(R.string.BlockUnblockDialog_you_will_be_able_to_call_and_message_each_other);
      builder.setPositiveButton(R.string.RecipientPreferenceActivity_unblock, ((dialog, which) -> onUnblock.run()));
      builder.setNegativeButton(android.R.string.cancel, null);
    }

    return builder;
  }
}
