package org.thoughtcrime.securesms.keyvalue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import org.signal.core.util.logging.Log;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class CertificateValues extends SignalStoreValues {

  private static final String TAG = Log.tag(CertificateValues.class);

  private static final String SEALED_SENDER_CERT_ACI_AND_E164 = "certificate.uuidAndE164";
  private static final String SEALED_SENDER_CERT_ACI_ONLY     = "certificate.uuidOnly";
  private static final String LAST_ROTATION_TIME              = "certificate.lastRotationTime";

  private static final long NEVER_ROTATED            = -1;
  private static final long LEGACY_ROTATION_INTERVAL = TimeUnit.DAYS.toMillis(1);

  CertificateValues(@NonNull KeyValueStore store, @NonNull Context context) {
    super(store);

    if (!store.containsKey(LAST_ROTATION_TIME)) {
      migrateFromSharedPrefsV1(context);
    }
  }

  @Override
  void onFirstEverAppLaunch() {
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Collections.emptyList();
  }

  @WorkerThread
  public void setUnidentifiedAccessCertificate(@NonNull CertificateType certificateType,
                                               @Nullable byte[] certificate)
  {
    KeyValueStore.Writer writer = getStore().beginWrite();

    switch (certificateType) {
      case ACI_AND_E164: writer.putBlob(SEALED_SENDER_CERT_ACI_AND_E164, certificate); break;
      case ACI_ONLY    : writer.putBlob(SEALED_SENDER_CERT_ACI_ONLY, certificate);     break;
      default          : throw new AssertionError();
    }

    writer.commit();
  }

  public @Nullable byte[] getUnidentifiedAccessCertificate(@NonNull CertificateType certificateType) {
    switch (certificateType) {
      case ACI_AND_E164: return getBlob(SEALED_SENDER_CERT_ACI_AND_E164, null);
      case ACI_ONLY    : return getBlob(SEALED_SENDER_CERT_ACI_ONLY, null);
      default          : throw new AssertionError();
    }
  }

  public long getLastRotationTime() {
    return getLong(LAST_ROTATION_TIME, NEVER_ROTATED);
  }

  public void setLastRotationTime(long lastRotationTime) {
    putLong(LAST_ROTATION_TIME, lastRotationTime);
  }

  /**
   * Do not alter. If you need to migrate more stuff, create a new method.
   * <p>
   * The legacy value held the next rotation time, always written as the clock at the last rotation plus a day.
   */
  private void migrateFromSharedPrefsV1(@NonNull Context context) {
    Log.i(TAG, "[V1] Migrating certificate values from shared prefs.");

    SharedPreferences sharedPrefs            = PreferenceManager.getDefaultSharedPreferences(context);
    long              legacyNextRotationTime = sharedPrefs.getLong("pref_unidentified_access_certificate_rotation_time", 0);

    putLong(LAST_ROTATION_TIME, legacyNextRotationTime > 0 ? legacyNextRotationTime - LEGACY_ROTATION_INTERVAL : NEVER_ROTATED);

    sharedPrefs.edit()
               .remove("pref_unidentified_access_certificate_rotation_time")
               .apply();
  }

}
