package org.thoughtcrime.securesms.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicReference;

public final class NotificationController implements AutoCloseable {

  private final @NonNull Context context;
  private final int id;

  private int     progress;
  private int     progressMax;
  private boolean indeterminate;
  private long    percent = -1;

  private final AtomicReference<GenericForegroundService> service = new AtomicReference<>();

  NotificationController(@NonNull Context context, int id) {
    this.context = context;
    this.id      = id;

    bindToService();
  }

  private void bindToService() {
    context.bindService(new Intent(context, GenericForegroundService.class), new ServiceConnection() {
      @Override
      public void onServiceConnected(ComponentName name, IBinder service) {
        GenericForegroundService.LocalBinder binder       = (GenericForegroundService.LocalBinder) service;
        GenericForegroundService genericForegroundService = binder.getService();

        NotificationController.this.service.set(genericForegroundService);

        updateProgressOnService();
      }

      @Override
      public void onServiceDisconnected(ComponentName name) {
        service.set(null);
      }
    }, Context.BIND_AUTO_CREATE);
  }

  public int getId() {
    return id;
  }

  @Override
  public void close() {
    GenericForegroundService.stopForegroundTask(context, id);
  }

  public void setIndeterminateProgress() {
    setProgress(0, 0, true);
  }

  public void setProgress(long newProgressMax, long newProgress) {
    setProgress((int) newProgressMax, (int) newProgress, false);
  }

  private synchronized void setProgress(int newProgressMax, int newProgress, boolean indeterminant) {
    int newPercent = newProgressMax != 0 ? 100 * newProgress / newProgressMax : -1;

    boolean same = newPercent == percent && indeterminate == indeterminant;

    percent       = newPercent;
    progress      = newProgress;
    progressMax   = newProgressMax;
    indeterminate = indeterminant;

    if (same) return;

    updateProgressOnService();
  }

  private synchronized void updateProgressOnService() {
    GenericForegroundService genericForegroundService = service.get();

    if (genericForegroundService == null) return;

    genericForegroundService.replaceProgress(id, progressMax, progress, indeterminate);
  }
}
