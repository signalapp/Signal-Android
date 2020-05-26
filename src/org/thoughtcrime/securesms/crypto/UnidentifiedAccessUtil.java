package org.thoughtcrime.securesms.crypto;


import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import org.signal.libsignal.metadata.SignalProtos;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class UnidentifiedAccessUtil {

  private static final String TAG = UnidentifiedAccessUtil.class.getSimpleName();

  public static CertificateValidator getCertificateValidator() {
    return new CertificateValidator();
  }

  @WorkerThread
  public static Optional<UnidentifiedAccessPair> getAccessFor(@NonNull Context context,
                                                              @NonNull Recipient recipient)
  {
    if (!TextSecurePreferences.isUnidentifiedDeliveryEnabled(context)) {
      Log.i(TAG, "Unidentified delivery is disabled. [other]");
      return Optional.absent();
    }

    try {
      byte[] theirUnidentifiedAccessKey       = getTargetUnidentifiedAccessKey(recipient);
      byte[] ourUnidentifiedAccessKey         = getSelfUnidentifiedAccessKey(context);
      byte[] ourUnidentifiedAccessCertificate = getUnidentifiedAccessCertificate(context);

      if (TextSecurePreferences.isUniversalUnidentifiedAccess(context)) {
        ourUnidentifiedAccessKey = Util.getSecretBytes(16);
      }

      Log.i(TAG, "Their access key present? " + (theirUnidentifiedAccessKey != null) +
                 " | Our access key present? " + (ourUnidentifiedAccessKey != null) +
                 " | Our certificate present? " + (ourUnidentifiedAccessCertificate != null));

      if (theirUnidentifiedAccessKey != null &&
          ourUnidentifiedAccessKey != null   &&
          ourUnidentifiedAccessCertificate != null)
      {
        return Optional.of(new UnidentifiedAccessPair(new UnidentifiedAccess(theirUnidentifiedAccessKey,
                                                                             ourUnidentifiedAccessCertificate),
                                                      new UnidentifiedAccess(ourUnidentifiedAccessKey,
                                                                             ourUnidentifiedAccessCertificate)));
      }

      return Optional.absent();
    } catch (InvalidCertificateException e) {
      Log.w(TAG, e);
      return Optional.absent();
    }
  }

  public static Optional<UnidentifiedAccessPair> getAccessForSync(@NonNull Context context) {
    if (!TextSecurePreferences.isUnidentifiedDeliveryEnabled(context)) {
      Log.i(TAG, "Unidentified delivery is disabled. [self]");
      return Optional.absent();
    }

    try {
      byte[] ourUnidentifiedAccessKey         = getSelfUnidentifiedAccessKey(context);
      byte[] ourUnidentifiedAccessCertificate = getUnidentifiedAccessCertificate(context);

      if (TextSecurePreferences.isUniversalUnidentifiedAccess(context)) {
        ourUnidentifiedAccessKey = Util.getSecretBytes(16);
      }

      if (ourUnidentifiedAccessKey != null && ourUnidentifiedAccessCertificate != null) {
        return Optional.of(new UnidentifiedAccessPair(new UnidentifiedAccess(ourUnidentifiedAccessKey,
                                                                             ourUnidentifiedAccessCertificate),
                                                      new UnidentifiedAccess(ourUnidentifiedAccessKey,
                                                                             ourUnidentifiedAccessCertificate)));
      }

      return Optional.absent();
    } catch (InvalidCertificateException e) {
      Log.w(TAG, e);
      return Optional.absent();
    }
  }

  public static @NonNull byte[] getSelfUnidentifiedAccessKey(@NonNull Context context) {
    return UnidentifiedAccess.deriveAccessKeyFrom(ProfileKeyUtil.getProfileKey(context));
  }

  private static @Nullable byte[] getTargetUnidentifiedAccessKey(@NonNull Recipient recipient) {
    byte[] theirProfileKey = recipient.resolve().getProfileKey();

    if (theirProfileKey == null) return Util.getSecretBytes(16);
    else                         return UnidentifiedAccess.deriveAccessKeyFrom(theirProfileKey);
    
  }

  private static @Nullable byte[] getUnidentifiedAccessCertificate(Context context) {
    String ourNumber = TextSecurePreferences.getLocalNumber(context);
    if (ourNumber != null) {
      SignalProtos.SenderCertificate certificate = SignalProtos.SenderCertificate.newBuilder()
                                                                                 .setSender(ourNumber)
                                                                                 .setSenderDevice(SignalServiceAddress.DEFAULT_DEVICE_ID)
                                                                                 .build();
      return certificate.toByteArray();
    }

    return null;
  }
}
