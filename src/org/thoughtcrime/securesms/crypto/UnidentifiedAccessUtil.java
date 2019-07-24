package org.thoughtcrime.securesms.crypto;


import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;

import java.io.IOException;

public class UnidentifiedAccessUtil {

  private static final String TAG = UnidentifiedAccessUtil.class.getSimpleName();

  public static CertificateValidator getCertificateValidator() {
    try {
      ECPublicKey unidentifiedSenderTrustRoot = Curve.decodePoint(Base64.decode(BuildConfig.UNIDENTIFIED_SENDER_TRUST_ROOT), 0);
      return new CertificateValidator(unidentifiedSenderTrustRoot);
    } catch (InvalidKeyException | IOException e) {
      throw new AssertionError(e);
    }
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
      byte[] ourUnidentifiedAccessCertificate = TextSecurePreferences.getUnidentifiedAccessCertificate(context);

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
      byte[] ourUnidentifiedAccessCertificate = TextSecurePreferences.getUnidentifiedAccessCertificate(context);

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

    switch (recipient.resolve().getUnidentifiedAccessMode()) {
      case UNKNOWN:
        if (theirProfileKey == null) return Util.getSecretBytes(16);
        else                         return UnidentifiedAccess.deriveAccessKeyFrom(theirProfileKey);
      case DISABLED:
        return null;
      case ENABLED:
        if (theirProfileKey == null) return null;
        else                         return UnidentifiedAccess.deriveAccessKeyFrom(theirProfileKey);
      case UNRESTRICTED:
        return Util.getSecretBytes(16);
      default:
        throw new AssertionError("Unknown mode: " + recipient.getUnidentifiedAccessMode().getMode());
    }
  }
}
