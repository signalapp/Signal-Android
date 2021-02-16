package org.thoughtcrime.securesms.jobs;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.ConversationUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.thoughtcrime.securesms.util.ConversationUtil.CONVERSATION_SUPPORT_VERSION;

/**
 * On some devices, interacting with the ShortcutManager can take a very long time (several seconds).
 * So, we interact with it in a job instead, and keep it in one queue so it can't starve the other
 * job runners.
 */
public class ConversationShortcutUpdateJob extends BaseJob {

  private static final String TAG = Log.tag(ConversationShortcutUpdateJob.class);

  public static final String KEY = "ConversationShortcutUpdateJob";

  public static void enqueue() {
    if (Build.VERSION.SDK_INT >= CONVERSATION_SUPPORT_VERSION) {
      ApplicationDependencies.getJobManager().add(new ConversationShortcutUpdateJob());
    }
  }

  private ConversationShortcutUpdateJob() {
    this(new Parameters.Builder()
                       .setQueue("ConversationShortcutUpdateJob")
                       .setLifespan(TimeUnit.MINUTES.toMillis(15))
                       .setMaxInstancesForFactory(1)
                       .build());
  }

  private ConversationShortcutUpdateJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  @RequiresApi(CONVERSATION_SUPPORT_VERSION)
  protected void onRun() throws Exception {
    if (TextSecurePreferences.isScreenLockEnabled(context)) {
      Log.i(TAG, "Screen lock enabled. Clearing shortcuts.");
      ConversationUtil.clearAllShortcuts(context);
      return;
    }

    ThreadDatabase  threadDatabase = DatabaseFactory.getThreadDatabase(context);
    int             maxShortcuts   = ConversationUtil.getMaxShortcuts(context);
    List<Recipient> ranked         = new ArrayList<>(maxShortcuts);

    try (ThreadDatabase.Reader reader = threadDatabase.readerFor(threadDatabase.getRecentConversationList(maxShortcuts, false, false))) {
      ThreadRecord record;
      while ((record = reader.getNext()) != null) {
        ranked.add(record.getRecipient().resolve());
      }
    }

    boolean success = ConversationUtil.setActiveShortcuts(context, ranked);

    if (!success) {
      throw new RetryLaterException();
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof RetryLaterException;
  }

  @Override
  public void onFailure() {
  }

  public static class Factory implements Job.Factory<ConversationShortcutUpdateJob> {
    @Override
    public @NonNull ConversationShortcutUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new ConversationShortcutUpdateJob(parameters);
    }
  }
}
