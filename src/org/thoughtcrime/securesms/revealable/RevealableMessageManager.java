package org.thoughtcrime.securesms.revealable;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.service.TimedEventManager;

/**
 * Manages clearing removable message content after they're opened.
 */
public class RevealableMessageManager extends TimedEventManager<RevealExpirationInfo> {

  private static final String TAG = Log.tag(RevealableMessageManager.class);

  private final MmsDatabase        mmsDatabase;
  private final AttachmentDatabase attachmentDatabase;

  public RevealableMessageManager(@NonNull Application application) {
    super(application, "RevealableMessageManager");

    this.mmsDatabase        = DatabaseFactory.getMmsDatabase(application);
    this.attachmentDatabase = DatabaseFactory.getAttachmentDatabase(application);
    
    scheduleIfNecessary();
  }

  @WorkerThread
  @Override
  protected @Nullable RevealExpirationInfo getNextClosestEvent() {
    RevealExpirationInfo expirationInfo = mmsDatabase.getNearestExpiringRevealableMessage();

    if (expirationInfo != null) {
      Log.i(TAG, "Next closest expiration is in " + getDelayForEvent(expirationInfo) + " ms for messsage " + expirationInfo.getMessageId() + ".");
    } else {
      Log.i(TAG, "No messages to schedule.");
    }

    return expirationInfo;
  }

  @WorkerThread
  @Override
  protected void executeEvent(@NonNull RevealExpirationInfo event) {
    Log.i(TAG, "Deleting attachments for message " + event.getMessageId());
    attachmentDatabase.deleteAttachmentFilesForMessage(event.getMessageId());
  }

  @WorkerThread
  @Override
  protected long getDelayForEvent(@NonNull RevealExpirationInfo event) {
    if (event.getRevealStartTime() == 0) {
      long expiresAt = event.getReceiveTime() + RevealableUtil.MAX_LIFESPAN;
      long timeLeft  = expiresAt - System.currentTimeMillis();

      return Math.max(0, timeLeft);
    } else {
      long timeSinceStart = System.currentTimeMillis() - event.getRevealStartTime();
      long timeLeft       = event.getRevealDuration() - timeSinceStart;

      return Math.max(0, timeLeft);
    }
  }

  @AnyThread
  @Override
  protected void scheduleAlarm(@NonNull Application application, long delay) {
    setAlarm(application, delay, RevealAlarm.class);
  }

  public static class RevealAlarm extends BroadcastReceiver {

    private static final String TAG = Log.tag(RevealAlarm.class);

    @Override
    public void onReceive(Context context, Intent intent) {
      Log.d(TAG, "onReceive()");
      ApplicationContext.getInstance(context).getRevealableMessageManager().scheduleIfNecessary();
    }
  }
}
