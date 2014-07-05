package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.push.PushServiceSocketFactory;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.state.DeviceKeyRecord;
import org.whispersystems.libaxolotl.state.DeviceKeyStore;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.crypto.PreKeyUtil;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.storage.TextSecurePreKeyStore;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PreKeyService extends Service {

  private static final String TAG            = PreKeyService.class.getSimpleName();
  public static final  String REFRESH_ACTION = "org.thoughtcrime.securesms.PreKeyService.REFRESH";

  private static final int PREKEY_MINIMUM = 10;

  private final Executor executor = Executors.newSingleThreadExecutor();

  public static void initiateRefresh(Context context, MasterSecret masterSecret) {
    Intent intent = new Intent(context, PreKeyService.class);
    intent.setAction(PreKeyService.REFRESH_ACTION);
    intent.putExtra("master_secret", masterSecret);
    context.startService(intent);
  }

  @Override
  public int onStartCommand(Intent intent, int flats, int startId) {
    if (REFRESH_ACTION.equals(intent.getAction())) {
      MasterSecret masterSecret = intent.getParcelableExtra("master_secret");
      executor.execute(new RefreshTask(this, masterSecret));
    }

    return START_NOT_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private static class RefreshTask implements Runnable {

    private final Context      context;
    private final MasterSecret masterSecret;

    public RefreshTask(Context context, MasterSecret masterSecret) {
      this.context      = context.getApplicationContext();
      this.masterSecret = masterSecret;
    }

    public void run() {
      DeviceKeyRecord deviceKeyRecord = null;

      try {
        if (!TextSecurePreferences.isPushRegistered(context)) return;

        PushServiceSocket socket        = PushServiceSocketFactory.create(context);
        int               availableKeys = socket.getAvailablePreKeys();

        if (availableKeys >= PREKEY_MINIMUM) {
          Log.w(TAG, "Available keys sufficient: " + availableKeys);
          return;
        }

        List<PreKeyRecord> preKeyRecords       = PreKeyUtil.generatePreKeys(context, masterSecret);
        PreKeyRecord       lastResortKeyRecord = PreKeyUtil.generateLastResortKey(context, masterSecret);
        IdentityKeyPair    identityKey         = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);

        deviceKeyRecord = PreKeyUtil.generateDeviceKey(context, masterSecret, identityKey);

        Log.w(TAG, "Registering new prekeys...");

        socket.registerPreKeys(identityKey.getPublicKey(), lastResortKeyRecord,
                               deviceKeyRecord, preKeyRecords);

        removeOldDeviceKeysIfNecessary(deviceKeyRecord);
      } catch (IOException e) {
        Log.w(TAG, e);
        if (deviceKeyRecord != null) {
          Log.w(TAG, "Remote store failed, removing generated device key: " + deviceKeyRecord.getId());
          new TextSecurePreKeyStore(context, masterSecret).removeDeviceKey(deviceKeyRecord.getId());
        }
      }
    }

    private void removeOldDeviceKeysIfNecessary(DeviceKeyRecord currentDeviceKey) {
      DeviceKeyStore            deviceKeyStore = new TextSecurePreKeyStore(context, masterSecret);
      List<DeviceKeyRecord>     records        = deviceKeyStore.loadDeviceKeys();
      Iterator<DeviceKeyRecord> iterator       = records.iterator();

      while (iterator.hasNext()) {
        if (iterator.next().getId() == currentDeviceKey.getId()) {
          iterator.remove();
        }
      }

      DeviceKeyRecord[] recordsArray = (DeviceKeyRecord[])records.toArray();
      Arrays.sort(recordsArray, new Comparator<DeviceKeyRecord>() {
        @Override
        public int compare(DeviceKeyRecord lhs, DeviceKeyRecord rhs) {
          if      (lhs.getTimestamp() < rhs.getTimestamp()) return -1;
          else if (lhs.getTimestamp() > rhs.getTimestamp()) return 1;
          else                                              return 0;
        }
      });

      Log.w(TAG, "Existing device key record count: " + recordsArray.length);

      if (recordsArray.length > 3) {
        long              oldTimestamp = System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000);
        DeviceKeyRecord[] oldRecords   = Arrays.copyOf(recordsArray, recordsArray.length - 1);

        for (DeviceKeyRecord oldRecord : oldRecords) {
          Log.w(TAG, "Old device key record timestamp: " + oldRecord.getTimestamp());

          if (oldRecord.getTimestamp() <= oldTimestamp) {
            Log.w(TAG, "Remove device key record: " + oldRecord.getId());
            deviceKeyStore.removeDeviceKey(oldRecord.getId());
          }
        }
      }
    }
  }

}
