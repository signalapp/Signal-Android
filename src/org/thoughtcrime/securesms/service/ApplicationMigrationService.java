package org.thoughtcrime.securesms.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.widget.RemoteViews;

import org.thoughtcrime.securesms.ConversationListActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.SmsMigrator;

public class ApplicationMigrationService extends Service
    implements SmsMigrator.SmsMigrationProgressListener
  {

  public static final int PROGRESS_UPDATE   = 1;
  public static final int PROGRESS_COMPLETE = 2;

  public static final String MIGRATE_DATABASE  = "org.thoughtcrime.securesms.ApplicationMigration.MIGRATE_DATABSE";

  private final Binder binder       = new ApplicationMigrationBinder();
  private boolean isMigrating       = false;
  private Handler handler           = null;
  private Notification notification = null;

  @Override
  public void onStart(Intent intent, int startId) {
    if (intent == null) return;

    if (intent.getAction() != null && intent.getAction().equals(MIGRATE_DATABASE)) {
      handleDatabaseMigration((MasterSecret)intent.getParcelableExtra("master_secret"));
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  private void handleDatabaseMigration(final MasterSecret masterSecret) {
    this.notification = initializeBackgroundNotification();

    final PowerManager power = (PowerManager)getSystemService(Context.POWER_SERVICE);
    final WakeLock wakeLock  = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Migration");

    new Thread() {
      @Override
      public void run() {
        try {
          wakeLock.acquire();

          setMigrating(true);
          SmsMigrator.migrateDatabase(ApplicationMigrationService.this,
                                      masterSecret,
                                      ApplicationMigrationService.this);
          setMigrating(false);

          if (handler != null) {
            handler.obtainMessage(PROGRESS_COMPLETE).sendToTarget();
          }

          stopForeground(true);
        } finally {
          wakeLock.release();
          stopService(new Intent(ApplicationMigrationService.this,
                                 ApplicationMigrationService.class));
        }
      }
    }.start();
  }

  private Notification initializeBackgroundNotification() {
    Intent intent               = new Intent(this, ConversationListActivity.class);
    Notification notification   = new Notification(R.drawable.icon, "Migrating",
                                                   System.currentTimeMillis());

    notification.flags       = notification.flags | Notification.FLAG_ONGOING_EVENT;
    notification.contentView = new RemoteViews(getApplicationContext().getPackageName(),
                                               R.layout.migration_notification_progress);

    notification.contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
    notification.contentView.setImageViewResource(R.id.status_icon, R.drawable.icon);
    notification.contentView.setTextViewText(R.id.status_text, "Migrating System Text Messages");
    notification.contentView.setProgressBar(R.id.status_progress, 10000, 0, false);

    stopForeground(true);
    startForeground(4242, notification);

    return notification;
  }

  private synchronized void setMigrating(boolean isMigrating) {
    this.isMigrating = isMigrating;
  }

  public synchronized boolean isMigrating() {
    return isMigrating;
  }

  public void setHandler(Handler handler) {
    this.handler = handler;
  }

  public class ApplicationMigrationBinder extends Binder {
    public ApplicationMigrationService getService() {
      return ApplicationMigrationService.this;
    }
  }

  @Override
  public void progressUpdate(int primaryProgress, int secondaryProgress) {
    if (handler != null) {
      handler.obtainMessage(PROGRESS_UPDATE, primaryProgress, secondaryProgress).sendToTarget();
    }

    if (notification != null && secondaryProgress == 0) {
      notification.contentView.setProgressBar(R.id.status_progress, 10000, primaryProgress, false);

      NotificationManager notificationManager =
          (NotificationManager)getApplicationContext()
            .getSystemService(Context.NOTIFICATION_SERVICE);

      notificationManager.notify(4242, notification);
    }
  }
}
