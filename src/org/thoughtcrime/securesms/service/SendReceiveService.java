/** 
 * Copyright (C) 2011 Whisper Systems
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.service;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.CanonicalSessionMigrator;
import org.thoughtcrime.securesms.util.WorkerThread;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

/**
 * Services that handles sending/receiving of SMS/MMS.
 * 
 * @author Moxie Marlinspike
 */

public class SendReceiveService extends Service {

  public static final String SEND_SMS_ACTION                  = "org.thoughtcrime.securesms.SendReceiveService.SEND_SMS_ACTION";
  public static final String SENT_SMS_ACTION                  = "org.thoughtcrime.securesms.SendReceiveService.SENT_SMS_ACTION";
  public static final String RECEIVE_SMS_ACTION               = "org.thoughtcrime.securesms.SendReceiveService.RECEIVE_SMS_ACTION";
  public static final String SEND_MMS_ACTION                  = "org.thoughtcrime.securesms.SendReceiveService.SEND_MMS_ACTION";
  public static final String SEND_MMS_CONNECTIVITY_ACTION     = "org.thoughtcrime.securesms.SendReceiveService.SEND_MMS_CONNECTIVITY_ACTION";
  public static final String RECEIVE_MMS_ACTION               = "org.thoughtcrime.securesms.SendReceiveService.RECEIVE_MMS_ACTION";
  public static final String DOWNLOAD_MMS_ACTION              = "org.thoughtcrime.securesms.SendReceiveService.DOWNLOAD_MMS_ACTION";
  public static final String DOWNLOAD_MMS_CONNECTIVITY_ACTION = "org.thoughtcrime.securesms.SendReceiveService.DOWNLOAD_MMS_CONNECTIVITY_ACTION";
	
  private static final int SEND_SMS              = 0;
  private static final int RECEIVE_SMS           = 1;
  private static final int SEND_MMS              = 2;
  private static final int RECEIVE_MMS           = 3;
  private static final int DOWNLOAD_MMS          = 4;
	
  private ToastHandler toastHandler;
	
  private  SmsReceiver   smsReceiver;
  private  SmsSender     smsSender;
  private  MmsReceiver   mmsReceiver;
  private  MmsSender     mmsSender;
  private  MmsDownloader mmsDownloader;
	
  private MasterSecret masterSecret;
  private NewKeyReceiver receiver;
  private List<Runnable> workQueue;
  private List<Runnable> pendingSecretList;
  private Thread workerThread;
	
  @Override
    public void onCreate() {
    initializeHandlers();
    initializeProcessors();
    initializeAddressCanonicalization();
    initializeWorkQueue();
    initializeMasterSecret();
  }
	
  @Override
  public void onStart(Intent intent, int startId) {
    if (intent.getAction().equals(SEND_SMS_ACTION))
      scheduleSecretRequiredIntent(SEND_SMS, intent);
    else if (intent.getAction().equals(RECEIVE_SMS_ACTION))
      scheduleIntent(RECEIVE_SMS, intent);
    else if (intent.getAction().equals(SENT_SMS_ACTION))
      scheduleIntent(RECEIVE_SMS, intent);
    else if (intent.getAction().equals(SEND_MMS_ACTION) || intent.getAction().equals(SEND_MMS_CONNECTIVITY_ACTION))
      scheduleSecretRequiredIntent(SEND_MMS, intent);
    else if (intent.getAction().equals(RECEIVE_MMS_ACTION))
      scheduleIntent(RECEIVE_MMS, intent);
    else if (intent.getAction().equals(DOWNLOAD_MMS_ACTION) || intent.getAction().equals(DOWNLOAD_MMS_CONNECTIVITY_ACTION))
      scheduleSecretRequiredIntent(DOWNLOAD_MMS, intent);
    else 
      Log.w("SendReceiveService", "Received intent with unknown action: " + intent.getAction());
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
	
  private void initializeHandlers() {
    toastHandler = new ToastHandler();
  }
	
  private void initializeProcessors() {
    smsReceiver    = new SmsReceiver(this);
    smsSender      = new SmsSender(this);
    mmsReceiver    = new MmsReceiver(this);
    mmsSender      = new MmsSender(this);
    mmsDownloader  = new MmsDownloader(this, toastHandler);
  }
	
  private void initializeWorkQueue() {
    pendingSecretList = new LinkedList<Runnable>();
    workQueue         = new LinkedList<Runnable>();
    workerThread      = new WorkerThread(workQueue, "SendReceveService-WorkerThread");

    workerThread.start();
  }	
	
  private void initializeMasterSecret() {
    receiver            = new NewKeyReceiver();
    IntentFilter filter = new IntentFilter(KeyCachingService.NEW_KEY_EVENT);
    registerReceiver(receiver, filter, KeyCachingService.KEY_PERMISSION, null);

    Intent bindIntent   = new Intent(this, KeyCachingService.class);
    bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
  }
	
  private void initializeWithMasterSecret(MasterSecret masterSecret) {
    Log.w("SendReceiveService", "SendReceive service got master secret: " + masterSecret);
		
    if (masterSecret != null) {
      synchronized (workQueue) {
        this.masterSecret = masterSecret;
				
        Iterator<Runnable> iterator = pendingSecretList.iterator();
        while (iterator.hasNext()) 
          workQueue.add(iterator.next());
				
        workQueue.notifyAll();
      }
    }
  }

  private void initializeAddressCanonicalization() {
    CanonicalSessionMigrator.migrateSessions(this);
  }
	
  private void scheduleIntent(int what, Intent intent) {
    Runnable work = new SendReceiveWorkItem(intent, what);
		
    synchronized (workQueue) {
      workQueue.add(work);
      workQueue.notifyAll();
    }
  }
	
  private void scheduleSecretRequiredIntent(int what, Intent intent) {
    Runnable work = new SendReceiveWorkItem(intent, what);
		
    synchronized (workQueue) {
      if (masterSecret != null) {
        workQueue.add(work);
        workQueue.notifyAll();
      } else { 
        pendingSecretList.add(work);			
      }
    }
  }
	
  private class SendReceiveWorkItem implements Runnable {
		
    private final Intent intent;
    private final int what;
		
    public SendReceiveWorkItem(Intent intent, int what) {
      this.intent = intent;
      this.what   = what;
    }
		
    public void run() {
      switch (what) {
      case RECEIVE_SMS:	smsReceiver.process(masterSecret, intent);   return;
      case SEND_SMS:		smsSender.process(masterSecret, intent);     return;
      case RECEIVE_MMS:   mmsReceiver.process(masterSecret, intent);   return;
      case SEND_MMS:      mmsSender.process(masterSecret, intent);     return;
      case DOWNLOAD_MMS:  mmsDownloader.process(masterSecret, intent); return;			
      }
    }
  }
	
  public class ToastHandler extends Handler {
    public void makeToast(String toast) {
      Message message = this.obtainMessage();
      message.obj     = toast;
      this.sendMessage(message);
    }
    @Override
      public void handleMessage(Message message) {
      Toast.makeText(SendReceiveService.this, (String)message.obj, Toast.LENGTH_LONG).show();
    }
  }

  private ServiceConnection serviceConnection = new ServiceConnection() {
      public void onServiceConnected(ComponentName className, IBinder service) {
        KeyCachingService keyCachingService  = ((KeyCachingService.KeyCachingBinder)service).getService();
        MasterSecret masterSecret            = keyCachingService.getMasterSecret();

        initializeWithMasterSecret(masterSecret);

        SendReceiveService.this.unbindService(this);			
      }

      public void onServiceDisconnected(ComponentName name) {}
    };
	
  private class NewKeyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      Log.w("SendReceiveService", "Got a MasterSecret broadcast...");
      initializeWithMasterSecret((MasterSecret)intent.getParcelableExtra("master_secret"));
    }
  };
	
}
