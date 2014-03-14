package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import org.thoughtcrime.securesms.util.WorkerThread;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by asafh on 3/3/14.
 * This class is a base for a service that executes tasks that depend on the MasterSecret.
 * Inheritors should take care to call super.onCreate and onDestroy if they override them.
 */
public abstract class MasterSecretTaskService extends Service {
  private final String tag;

  private MasterSecret masterSecret;
  private boolean hasSecret;
  private NewKeyReceiver newKeyReceiver;
  private ClearKeyReceiver clearKeyReceiver;

  private List<Runnable> workQueue;
  private List<Runnable> pendingSecretList;


  public MasterSecretTaskService() {
    tag = getClass().getName();
  }

  public boolean hasSecret() {
    return hasSecret;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onCreate() {
    initializeWorkQueue();
    initializeMasterSecret();
  }

  @Override
  public void onDestroy() {
    Log.w(tag, "onDestroy()...");
    super.onDestroy();

    if (newKeyReceiver != null)
      unregisterReceiver(newKeyReceiver);

    if (clearKeyReceiver != null)
      unregisterReceiver(clearKeyReceiver);
  }

  private void initializeWorkQueue() {
    pendingSecretList = new LinkedList<Runnable>();
    workQueue = new LinkedList<Runnable>();

    Thread workerThread = new WorkerThread(workQueue, tag + "-WorkerThread");
    workerThread.start();
  }

  public void clearSecret() {
    synchronized (workQueue) {
      MasterSecretTaskService.this.hasSecret = false;
      workQueue.add(new Runnable() {
        @Override
        public void run() {
          Log.w(tag, "Running clear key work item...");

          synchronized (workQueue) {
            if (!MasterSecretTaskService.this.hasSecret) {
              Log.w(tag, "Actually clearing key...");
              MasterSecretTaskService.this.masterSecret = null;
            }
          }
        }
      });

      workQueue.notifyAll();
    }
  }

  private ServiceConnection serviceConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      KeyCachingService keyCachingService = ((KeyCachingService.KeyCachingBinder) service).getService();
      MasterSecret masterSecret = keyCachingService.getMasterSecret();

      initializeWithMasterSecret(masterSecret);

      MasterSecretTaskService.this.unbindService(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
  };


  private void initializeMasterSecret() {
    hasSecret = false;
    newKeyReceiver = new NewKeyReceiver();
    clearKeyReceiver = new ClearKeyReceiver();

    IntentFilter newKeyFilter = new IntentFilter(KeyCachingService.NEW_KEY_EVENT);
    registerReceiver(newKeyReceiver, newKeyFilter, KeyCachingService.KEY_PERMISSION, null);

    IntentFilter clearKeyFilter = new IntentFilter(KeyCachingService.CLEAR_KEY_EVENT);
    registerReceiver(clearKeyReceiver, clearKeyFilter, KeyCachingService.KEY_PERMISSION, null);

    Intent bindIntent = new Intent(this, KeyCachingService.class);
    startService(bindIntent);
    bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
  }

  private void initializeWithMasterSecret(MasterSecret masterSecret) {
    Log.w(tag, "Service got master secret.");

    if (masterSecret != null) {
      synchronized (workQueue) {
        this.masterSecret = masterSecret;
        this.hasSecret = true;

        Iterator<Runnable> iterator = pendingSecretList.iterator();
        while (iterator.hasNext())
          workQueue.add(iterator.next());

        pendingSecretList.clear(); //Added by asafh, I can't see where the pending secret
        // list is cleared otherwise, this place makes sense.

        workQueue.notifyAll();
      }
    }
  }

  public void scheduleSecretOptionalWork(MasterSecretTask task) {
    RunnableMasterSecretTaskWrapper work = new RunnableMasterSecretTaskWrapper(task);
    synchronized (workQueue) {
      workQueue.add(work);
      workQueue.notifyAll();
    }
  }

  public void scheduleSecretRequiredWork(MasterSecretTask task) {
    RunnableMasterSecretTaskWrapper work = new RunnableMasterSecretTaskWrapper(task);
    synchronized (workQueue) {
      if (hasSecret) {
        workQueue.add(work);
        workQueue.notifyAll();
      } else {
        pendingSecretList.add(work);
      }
    }
  }

  private class RunnableMasterSecretTaskWrapper implements Runnable {
    private final MasterSecretTask work;

    public RunnableMasterSecretTaskWrapper(MasterSecretTask work) {
      this.work = work;
    }

    @Override
    public void run() {
      work.call(MasterSecretTaskService.this.masterSecret);
    }
  }

  public static interface MasterSecretTask {
    void call(MasterSecret masterSecret);
  }

  private class NewKeyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.w(tag, "Got a MasterSecret broadcast...");
      initializeWithMasterSecret((MasterSecret) intent.getParcelableExtra("master_secret"));
    }
  }

  /**
   * This class receives broadcast notifications to clear the MasterSecret.
   * <p/>
   * We don't want to clear it immediately, since there are potentially jobs
   * in the work queue which require the master secret.  Instead, we reset a
   * flag so that new incoming jobs will be evaluated as if no mastersecret is
   * present.
   * <p/>
   * Then, we add a job to the end of the queue which actually clears the masterSecret
   * value.  That way all jobs before this moment will be processed correctly, and all
   * jobs after this moment will be evaluated as if no mastersecret is present (and potentially
   * held).
   * <p/>
   * When we go to actually clear the mastersecret, we ensure that the flag is still false.
   * This allows a new mastersecret broadcast to come in correctly without us clobbering it.
   */
  private class ClearKeyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.w(tag, "Got a clear mastersecret broadcast...");
      clearSecret();
    }
  }
}
