package org.thoughtcrime.securesms.jobs;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.AppCapabilities;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.keyvalue.KbsValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.registration.RegistrationRepository;
import org.thoughtcrime.securesms.registration.secondary.DeviceNameCipher;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.account.AccountAttributes;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class RefreshAttributesJob extends BaseJob {

  public static final String KEY = "RefreshAttributesJob";

  private static final String TAG = Log.tag(RefreshAttributesJob.class);

  private static final String KEY_FORCED = "forced";

  private static volatile boolean hasRefreshedThisAppCycle;

  private final boolean forced;

  public RefreshAttributesJob() {
    this(true);
  }

  /**
   * @param forced True if you want this job to run no matter what. False if you only want this job
   *               to run if it hasn't run yet this app cycle.
   */
  public RefreshAttributesJob(boolean forced) {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setQueue("RefreshAttributesJob")
                           .setMaxInstancesForFactory(2)
                           .setLifespan(TimeUnit.DAYS.toMillis(30))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build(),
         forced);
  }

  private RefreshAttributesJob(@NonNull Job.Parameters parameters, boolean forced) {
    super(parameters);
    this.forced = forced;
  }

  @Override
  public @NonNull Data serialize() {
    return new Data.Builder().putBoolean(KEY_FORCED, forced).build();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException {
    if (!SignalStore.account().isRegistered() || SignalStore.account().getE164() == null) {
      Log.w(TAG, "Not yet registered. Skipping.");
      return;
    }

    if (!forced && hasRefreshedThisAppCycle) {
      Log.d(TAG, "Already refreshed this app cycle. Skipping.");
      return;
    }

    int       registrationId              = SignalStore.account().getRegistrationId();
    boolean   fetchesMessages             = !SignalStore.account().isFcmEnabled() || SignalStore.internalValues().isWebsocketModeForced();
    byte[]    unidentifiedAccessKey       = UnidentifiedAccess.deriveAccessKeyFrom(ProfileKeyUtil.getSelfProfileKey());
    boolean   universalUnidentifiedAccess = TextSecurePreferences.isUniversalUnidentifiedAccess(context);
    String    registrationLockV1          = null;
    String    registrationLockV2          = null;
    KbsValues kbsValues                   = SignalStore.kbsValues();
    int       pniRegistrationId           = new RegistrationRepository(ApplicationDependencies.getApplication()).getPniRegistrationId();

    if (kbsValues.isV2RegistrationLockEnabled()) {
      registrationLockV2 = kbsValues.getRegistrationLockToken();
    } else if (TextSecurePreferences.isV1RegistrationLockEnabled(context)) {
      //noinspection deprecation Ok to read here as they have not migrated
      registrationLockV1 = TextSecurePreferences.getDeprecatedV1RegistrationLockPin(context);
    }

    boolean phoneNumberDiscoverable = SignalStore.phoneNumberPrivacy().getPhoneNumberListingMode().isDiscoverable();

    String deviceName = SignalStore.account().getDeviceName();
    byte[] encryptedDeviceName = (deviceName == null) ? null : DeviceNameCipher.encryptDeviceName(deviceName.getBytes(StandardCharsets.UTF_8), SignalStore.account().getAciIdentityKey());

    AccountAttributes.Capabilities capabilities = AppCapabilities.getCapabilities(kbsValues.hasPin() && !kbsValues.hasOptedOut());
    Log.i(TAG, "Calling setAccountAttributes() reglockV1? " + !TextUtils.isEmpty(registrationLockV1) + ", reglockV2? " + !TextUtils.isEmpty(registrationLockV2) + ", pin? " + kbsValues.hasPin() +
               "\n    Phone number discoverable : " + phoneNumberDiscoverable +
               "\n    Device Name : " + (encryptedDeviceName != null) +
               "\n  Capabilities: " + capabilities);

    SignalServiceAccountManager signalAccountManager = ApplicationDependencies.getSignalServiceAccountManager();
    signalAccountManager.setAccountAttributes(null,
                                              registrationId,
                                              fetchesMessages,
                                              registrationLockV1,
                                              registrationLockV2,
                                              unidentifiedAccessKey,
                                              universalUnidentifiedAccess,
                                              capabilities,
                                              phoneNumberDiscoverable,
                                              encryptedDeviceName,
                                              pniRegistrationId);

    hasRefreshedThisAppCycle = true;
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof NetworkFailureException;
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to update account attributes!");
  }

  public static class Factory implements Job.Factory<RefreshAttributesJob> {
    @Override
    public @NonNull RefreshAttributesJob create(@NonNull Parameters parameters, @NonNull org.thoughtcrime.securesms.jobmanager.Data data) {
      return new RefreshAttributesJob(parameters, data.getBooleanOrDefault(KEY_FORCED, true));
    }
  }
}
