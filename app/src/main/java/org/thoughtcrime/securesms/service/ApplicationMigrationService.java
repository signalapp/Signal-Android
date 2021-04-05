package org.thoughtcrime.securesms.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import androidx.core.app.NotificationCompat;

import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.SmsMigrator;
import org.thoughtcrime.securesms.database.SmsMigrator.ProgressDescription;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.NotificationIds;

import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

// FIXME: This class is nuts.
public class ApplicationMigrationService extends Service
    implements SmsMigrator.SmsMigrationProgressListener
{
  private static final String TAG               = ApplicationMigrationService.class.getSimpleName();
  public  static final String MIGRATE_DATABASE  = "org.thoughtcrime.securesms.ApplicationMigration.MIGRATE_DATABSE";
  public  static final String COMPLETED_ACTION  = "org.thoughtcrime.securesms.ApplicationMigrationService.COMPLETED";
  private static final String PREFERENCES_NAME  = "SecureSMS";
  private static final String DATABASE_MIGRATED = "migrated";

  private final BroadcastReceiver completedReceiver = new CompletedReceiver();
  private final Binder binder                       = new ApplicationMigrationBinder();
  private final Executor executor                   = Executors.newSingleThreadExecutor();

  private WeakReference<Handler>     handler      = null;
  private NotificationCompat.Builder notification = null;
  private ImportState                state        = new ImportState(ImportState.STATE_IDLE, null);

  @Override
  public void onCreate() {
    registerCompletedReceiver();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) return START_NOT_STICKY;

    if (intent.getAction() != null && intent.getAction().equals(MIGRATE_DATABASE)) {
      executor.execute(new ImportRunnable());
    }

    return START_NOT_STICKY;
  }

  @Override
  public void onDestroy() {
    unregisterCompletedReceiver();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  public void setImportStateHandler(Handler handler) {
    this.handler = new WeakReference<>(handler);
  }

  private void registerCompletedReceiver() {
    IntentFilter filter = new IntentFilter();
    filter.addAction(COMPLETED_ACTION);

    registerReceiver(completedReceiver, filter);
  }

  private void unregisterCompletedReceiver() {
    unregisterReceiver(completedReceiver);
  }

  private void notifyImportComplete() {
    Intent intent = new Intent();
    intent.setAction(COMPLETED_ACTION);

    sendOrderedBroadcast(intent, null);
  }

  @Override
  public void progressUpdate(ProgressDescription progress) {
    setState(new ImportState(ImportState.STATE_MIGRATING_IN_PROGRESS, progress));
  }

  public ImportState getState() {
    return state;
  }

  private void setState(ImportState state) {
    this.state = state;

    if (this.handler != null) {
      Handler handler = this.handler.get();

      if (handler != null) {
        handler.obtainMessage(state.state, state.progress).sendToTarget();
      }
    }

    if (state.progress != null && state.progress.secondaryComplete == 0) {
      updateBackgroundNotification(state.progress.primaryTotal, state.progress.primaryComplete);
    }
  }

  private void updateBackgroundNotification(int total, int complete) {
    notification.setProgress(total, complete, false);

    ((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE))
      .notify(NotificationIds.APPLICATION_MIGRATION, notification.build());
  }

  private NotificationCompat.Builder initializeBackgroundNotification() {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NotificationChannels.OTHER);

    builder.setSmallIcon(R.drawable.ic_notification);
    builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_notification));
    builder.setContentTitle(getString(R.string.ApplicationMigrationService_importing_text_messages));
    builder.setContentText(getString(R.string.ApplicationMigrationService_import_in_progress));
    builder.setOngoing(true);
    builder.setProgress(100, 0, false);
    // TODO [greyson] Navigation
    builder.setContentIntent(PendingIntent.getActivity(this, 0, MainActivity.clearTop(this), 0));

    stopForeground(true);
    startForeground(NotificationIds.APPLICATION_MIGRATION, builder.build());

    return builder;
  }

  private class ImportRunnable implements Runnable {

    ImportRunnable() {}

    @Override
    public void run() {
      notification              = initializeBackgroundNotification();
      PowerManager powerManager = (PowerManager)getSystemService(Context.POWER_SERVICE);
      WakeLock     wakeLock     = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "signal:migration");

      try {
        wakeLock.acquire();

        setState(new ImportState(ImportState.STATE_MIGRATING_BEGIN, null));

        SmsMigrator.migrateDatabase(ApplicationMigrationService.this,
                                    ApplicationMigrationService.this);

        setState(new ImportState(ImportState.STATE_MIGRATING_COMPLETE, null));

        setDatabaseImported(ApplicationMigrationService.this);
        stopForeground(true);
        notifyImportComplete();
        stopSelf();
      } finally {
        wakeLock.release();
      }
    }
  }

  public class ApplicationMigrationBinder extends Binder {
    public ApplicationMigrationService getService() {
      return ApplicationMigrationService.this;
    }
  }

  private static class CompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationChannels.OTHER);
      builder.setSmallIcon(R.drawable.ic_notification);
      builder.setContentTitle(context.getString(R.string.ApplicationMigrationService_import_complete));
      builder.setContentText(context.getString(R.string.ApplicationMigrationService_system_database_import_is_complete));
      // TODO [greyson] Navigation
      builder.setContentIntent(PendingIntent.getActivity(context, 0, MainActivity.clearTop(context), 0));
      builder.setWhen(System.currentTimeMillis());
      builder.setDefaults(Notification.DEFAULT_VIBRATE);
      builder.setAutoCancel(true);

      Notification notification = builder.build();
      ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(NotificationIds.SMS_IMPORT_COMPLETE, notification);
    }
  }

  public static class ImportState {
    public static final int STATE_IDLE                  = 0;
    public static final int STATE_MIGRATING_BEGIN       = 1;
    public static final int STATE_MIGRATING_IN_PROGRESS = 2;
    public static final int STATE_MIGRATING_COMPLETE    = 3;

    public int                 state;
    public ProgressDescription progress;

    public ImportState(int state, ProgressDescription progress) {
      this.state    = state;
      this.progress = progress;
    }
  }

  public static boolean isDatabaseImported(Context context) {
    return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
              .getBoolean(DATABASE_MIGRATED, false);
  }

  public static void setDatabaseImported(Context context) {
    context.getSharedPreferences(PREFERENCES_NAME, 0).edit().putBoolean(DATABASE_MIGRATED, true).apply();
  }
}
