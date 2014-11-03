package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.push.PushServiceSocketFactory;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyStore;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.PreKeyUtil;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.push.SignedPreKeyEntity;
import org.thoughtcrime.securesms.crypto.storage.TextSecurePreKeyStore;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PreKeyService extends Service {

  private static final String TAG                  = PreKeyService.class.getSimpleName();
  public  static final String REFRESH_ACTION       = "org.thoughtcrime.securesms.PreKeyService.REFRESH";
  public  static final String CLEAN_ACTION         = "org.thoughtcrime.securesms.PreKeyService.CLEAN";
  public  static final String CREATE_SIGNED_ACTION = "org.thoughtcrime.securesms.PreKeyService.CREATE_SIGNED";

  private static final int PREKEY_MINIMUM = 10;

  private final Executor executor = Executors.newSingleThreadExecutor();

  public static void initiateRefresh(Context context, MasterSecret masterSecret) {
    Intent intent = new Intent(context, PreKeyService.class);
    intent.setAction(PreKeyService.REFRESH_ACTION);
    intent.putExtra("master_secret", masterSecret);
    context.startService(intent);
  }

  public static void initiateClean(Context context, MasterSecret masterSecret) {
    Intent intent = new Intent(context, PreKeyService.class);
    intent.setAction(PreKeyService.CLEAN_ACTION);
    intent.putExtra("master_secret", masterSecret);
    context.startService(intent);
  }

  public static void initiateCreateSigned(Context context, MasterSecret masterSecret) {
    Intent intent = new Intent(context, PreKeyService.class);
    intent.setAction(PreKeyService.CREATE_SIGNED_ACTION);
    intent.putExtra("master_secret", masterSecret);
    context.startService(intent);
  }

  @Override
  public int onStartCommand(Intent intent, int flats, int startId) {
    if (intent             == null) return START_NOT_STICKY;
    if (intent.getAction() == null) return START_NOT_STICKY;

    MasterSecret masterSecret = intent.getParcelableExtra("master_secret");

    if (masterSecret == null) {
      Log.w(TAG, "No master secret!");
      return START_NOT_STICKY;
    }

    switch (intent.getAction()) {
      case REFRESH_ACTION:       executor.execute(new RefreshTask(this, masterSecret));            break;
      case CLEAN_ACTION:         executor.execute(new CleanSignedPreKeysTask(this, masterSecret)); break;
      case CREATE_SIGNED_ACTION: executor.execute(new CreateSignedPreKeyTask(this, masterSecret)); break;
    }

    return START_NOT_STICKY;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private static class CreateSignedPreKeyTask implements Runnable {

    private final Context      context;
    private final MasterSecret masterSecret;

    public CreateSignedPreKeyTask(Context context, MasterSecret masterSecret) {
      this.context      = context;
      this.masterSecret = masterSecret;
    }

    @Override
    public void run() {
      if (TextSecurePreferences.isSignedPreKeyRegistered(context)) {
        Log.w(TAG, "Signed prekey already registered...");
        return;
      }

      try {
        IdentityKeyPair    identityKeyPair    = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);
        SignedPreKeyRecord signedPreKeyRecord = PreKeyUtil.generateSignedPreKey(context, masterSecret, identityKeyPair);
        PushServiceSocket  socket             = PushServiceSocketFactory.create(context);

        socket.setCurrentSignedPreKey(signedPreKeyRecord);
        TextSecurePreferences.setSignedPreKeyRegistered(context, true);
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }
  }

  static class CleanSignedPreKeysTask implements Runnable {

    private final SignedPreKeyStore signedPreKeyStore;
    private final PushServiceSocket socket;

    public CleanSignedPreKeysTask(Context context, MasterSecret masterSecret) {
      this(new TextSecurePreKeyStore(context, masterSecret), PushServiceSocketFactory.create(context));
    }

    public CleanSignedPreKeysTask(SignedPreKeyStore signedPreKeyStore, PushServiceSocket socket) {
      this.signedPreKeyStore = signedPreKeyStore;
      this.socket            = socket;
    }

    public void run() {
      try {
        SignedPreKeyEntity currentSignedPreKey = socket.getCurrentSignedPreKey();

        if (currentSignedPreKey == null) return;

        SignedPreKeyRecord       currentRecord   = signedPreKeyStore.loadSignedPreKey(currentSignedPreKey.getKeyId());
        List<SignedPreKeyRecord> allRecords      = signedPreKeyStore.loadSignedPreKeys();
        List<SignedPreKeyRecord> oldRecords      = removeCurrentRecord(allRecords, currentRecord);
        SignedPreKeyRecord[]     oldRecordsArray = oldRecords.toArray(new SignedPreKeyRecord[0]);

        Arrays.sort(oldRecordsArray, new SignedPreKeySorter());

        Log.w(TAG, "Existing signed prekey record count: " + oldRecordsArray.length);

        if (oldRecordsArray.length > 3) {
          long                 oldTimestamp       = System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000);
          SignedPreKeyRecord[] deletionCandidates = Arrays.copyOf(oldRecordsArray, oldRecordsArray.length - 1);

          for (SignedPreKeyRecord deletionCandidate : deletionCandidates) {
            Log.w(TAG, "Old signed prekey record timestamp: " + deletionCandidate.getTimestamp());

            if (deletionCandidate.getTimestamp() <= oldTimestamp) {
              Log.w(TAG, "Remove signed prekey record: " + deletionCandidate.getId());
              signedPreKeyStore.removeSignedPreKey(deletionCandidate.getId());
            }
          }
        }
      } catch (IOException | InvalidKeyIdException e) {
        Log.w(TAG, e);
      }
    }

    private List<SignedPreKeyRecord> removeCurrentRecord(List<SignedPreKeyRecord> records,
                                                         SignedPreKeyRecord currentRecord)
    {
      List<SignedPreKeyRecord> others = new LinkedList<>();

      for (SignedPreKeyRecord record : records) {
        if (record.getId() != currentRecord.getId()) {
          others.add(record);
        }
      }

      return others;
    }
  }

  private static class RefreshTask implements Runnable {

    private final Context      context;
    private final MasterSecret masterSecret;

    public RefreshTask(Context context, MasterSecret masterSecret) {
      this.context      = context.getApplicationContext();
      this.masterSecret = masterSecret;
    }

    public void run() {
      try {
        if (!TextSecurePreferences.isPushRegistered(context)) return;

        PushServiceSocket socket        = PushServiceSocketFactory.create(context);
        int               availableKeys = socket.getAvailablePreKeys();

        if (availableKeys >= PREKEY_MINIMUM && TextSecurePreferences.isSignedPreKeyRegistered(context)) {
          Log.w(TAG, "Available keys sufficient: " + availableKeys);
          return;
        }

        List<PreKeyRecord> preKeyRecords       = PreKeyUtil.generatePreKeys(context, masterSecret);
        PreKeyRecord       lastResortKeyRecord = PreKeyUtil.generateLastResortKey(context, masterSecret);
        IdentityKeyPair    identityKey         = IdentityKeyUtil.getIdentityKeyPair(context, masterSecret);
        SignedPreKeyRecord signedPreKeyRecord  = PreKeyUtil.generateSignedPreKey(context, masterSecret, identityKey);

        Log.w(TAG, "Registering new prekeys...");

        socket.registerPreKeys(identityKey.getPublicKey(), lastResortKeyRecord,
                               signedPreKeyRecord, preKeyRecords);

        TextSecurePreferences.setSignedPreKeyRegistered(context, true);
        PreKeyService.initiateClean(context, masterSecret);
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    }
  }

  private static class SignedPreKeySorter implements Comparator<SignedPreKeyRecord> {
    @Override
    public int compare(SignedPreKeyRecord lhs, SignedPreKeyRecord rhs) {
      if      (lhs.getTimestamp() < rhs.getTimestamp()) return -1;
      else if (lhs.getTimestamp() > rhs.getTimestamp()) return 1;
      else                                              return 0;
    }
  }

}
