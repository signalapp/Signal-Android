package org.thoughtcrime.securesms.invites;

import android.content.Context;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.components.reminder.FirstInviteReminder;
import org.thoughtcrime.securesms.components.reminder.Reminder;
import org.thoughtcrime.securesms.components.reminder.SecondInviteReminder;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.signal.core.util.concurrent.SimpleTask;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class InviteReminderModel {

  private static final int FIRST_INVITE_REMINDER_MESSAGE_THRESHOLD  = 10;
  private static final int SECOND_INVITE_REMINDER_MESSAGE_THRESHOLD = 500;

  private final Context                       context;
  private final Repository                    repository;
  private final AtomicReference<ReminderInfo> reminderInfo = new AtomicReference<>();

  public InviteReminderModel(@NonNull Context context, @NonNull Repository repository) {
    this.context = context;
    this.repository = repository;
  }

  @MainThread
  public void loadReminder(LiveRecipient liveRecipient, Runnable reminderCheckComplete) {
    SimpleTask.run(() -> createReminderInfo(liveRecipient.resolve()), result -> {
      reminderInfo.set(result);
      reminderCheckComplete.run();
    });
  }

  @WorkerThread
  private @NonNull ReminderInfo createReminderInfo(Recipient recipient) {
    Recipient resolved = recipient.resolve();

    if (resolved.isRegistered() || resolved.isGroup() || resolved.hasSeenSecondInviteReminder()) {
      return new NoReminderInfo();
    }

    ThreadTable threadTable = SignalDatabase.threads();
    Long        threadId    = threadTable.getThreadIdFor(recipient.getId());

    if (threadId != null) {
      int conversationCount = SignalDatabase.messages().getInsecureMessageSentCount(threadId);

      if (conversationCount >= SECOND_INVITE_REMINDER_MESSAGE_THRESHOLD && !resolved.hasSeenSecondInviteReminder()) {
        return new SecondInviteReminderInfo(context, resolved, repository, repository.getPercentOfInsecureMessages(conversationCount));
      } else if (conversationCount >= FIRST_INVITE_REMINDER_MESSAGE_THRESHOLD && !resolved.hasSeenFirstInviteReminder()) {
        return new FirstInviteReminderInfo(context, resolved, repository, repository.getPercentOfInsecureMessages(conversationCount));
      }
    }
    return new NoReminderInfo();
  }

  public @NonNull Optional<Reminder> getReminder() {
    ReminderInfo info = reminderInfo.get();
    if (info == null) return Optional.empty();
    else              return Optional.ofNullable(info.reminder);
  }

  public void dismissReminder() {
    final ReminderInfo info = reminderInfo.getAndSet(null);

    SimpleTask.run(() -> {
      info.dismiss();
      return null;
    }, (v) -> {});
  }

  interface Repository {
    void setHasSeenFirstInviteReminder(Recipient recipient);
    void setHasSeenSecondInviteReminder(Recipient recipient);
    int getPercentOfInsecureMessages(int insecureCount);
  }

  private static abstract class ReminderInfo {

    private final Reminder reminder;

    ReminderInfo(Reminder reminder) {
      this.reminder = reminder;
    }

    @WorkerThread
    void dismiss() {
    }
  }

  private static class NoReminderInfo extends ReminderInfo {
    private NoReminderInfo() {
      super(null);
    }
  }

  private class FirstInviteReminderInfo extends ReminderInfo {

    private final Repository repository;
    private final Recipient  recipient;

    private FirstInviteReminderInfo(@NonNull Context context, @NonNull Recipient recipient, @NonNull Repository repository, int percentInsecure) {
      super(new FirstInviteReminder(context, recipient, percentInsecure));

      this.recipient  = recipient;
      this.repository = repository;
    }

    @Override
    @WorkerThread
    void dismiss() {
      repository.setHasSeenFirstInviteReminder(recipient);
    }
  }

  private static class SecondInviteReminderInfo extends ReminderInfo {

    private final Repository repository;
    private final Recipient  recipient;

    private SecondInviteReminderInfo(@NonNull Context context, @NonNull Recipient recipient, @NonNull Repository repository, int percentInsecure) {
      super(new SecondInviteReminder(context, recipient, percentInsecure));

      this.repository = repository;
      this.recipient  = recipient;
    }

    @Override
    @WorkerThread
    void dismiss() {
      repository.setHasSeenSecondInviteReminder(recipient);
    }
  }
}
