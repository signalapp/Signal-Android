package org.thoughtcrime.securesms.crypto;


import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.session.libsignal.metadata.SignalProtos;
import org.session.libsignal.utilities.logging.Log;
import org.session.libsession.messaging.threads.recipients.Recipient;
import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.Util;
import org.session.libsignal.libsignal.util.guava.Optional;
import org.session.libsignal.service.api.crypto.UnidentifiedAccess;
import org.session.libsignal.service.api.push.SignalServiceAddress;

public class UnidentifiedAccessUtil {

  private static final String TAG = UnidentifiedAccessUtil.class.getSimpleName();

  @WorkerThread
  public static Optional<UnidentifiedAccess> getAccessFor(@NonNull Context context,
                                                              @NonNull Recipient recipient)
  {
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
      return Optional.of(new UnidentifiedAccess(theirUnidentifiedAccessKey));
    }

    return Optional.absent();
  }

  public static Optional<UnidentifiedAccess> getAccessForSync(@NonNull Context context) {
    byte[] ourUnidentifiedAccessKey         = getSelfUnidentifiedAccessKey(context);
    byte[] ourUnidentifiedAccessCertificate = getUnidentifiedAccessCertificate(context);

    if (TextSecurePreferences.isUniversalUnidentifiedAccess(context)) {
      ourUnidentifiedAccessKey = Util.getSecretBytes(16);
    }

    if (ourUnidentifiedAccessKey != null && ourUnidentifiedAccessCertificate != null) {
      return Optional.of(new UnidentifiedAccess(ourUnidentifiedAccessKey));
    }

    return Optional.absent();
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
