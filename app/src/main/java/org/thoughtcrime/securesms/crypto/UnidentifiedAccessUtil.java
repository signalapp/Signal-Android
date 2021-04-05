package org.thoughtcrime.securesms.crypto;


import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.keyvalue.CertificateType;
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UnidentifiedAccessUtil {

  private static final String TAG = Log.tag(UnidentifiedAccessUtil.class);

  public static CertificateValidator getCertificateValidator() {
    try {
      ECPublicKey unidentifiedSenderTrustRoot = Curve.decodePoint(Base64.decode(BuildConfig.UNIDENTIFIED_SENDER_TRUST_ROOT), 0);
      return new CertificateValidator(unidentifiedSenderTrustRoot);
    } catch (InvalidKeyException | IOException e) {
      throw new AssertionError(e);
    }
  }

  @WorkerThread
  public static Optional<UnidentifiedAccessPair> getAccessFor(@NonNull Context context, @NonNull Recipient recipient) {
    return getAccessFor(context, recipient, true);
  }

  @WorkerThread
  public static Optional<UnidentifiedAccessPair> getAccessFor(@NonNull Context context, @NonNull Recipient recipient, boolean log) {
    return getAccessFor(context, Collections.singletonList(recipient), log).get(0);
  }

  @WorkerThread
  public static List<Optional<UnidentifiedAccessPair>> getAccessFor(@NonNull Context context, @NonNull List<Recipient> recipients) {
    return getAccessFor(context, recipients, true);
  }

  @WorkerThread
  public static List<Optional<UnidentifiedAccessPair>> getAccessFor(@NonNull Context context, @NonNull List<Recipient> recipients, boolean log) {
    byte[] ourUnidentifiedAccessKey = UnidentifiedAccess.deriveAccessKeyFrom(ProfileKeyUtil.getSelfProfileKey());

    if (TextSecurePreferences.isUniversalUnidentifiedAccess(context)) {
      ourUnidentifiedAccessKey = Util.getSecretBytes(16);
    }

    List<Optional<UnidentifiedAccessPair>> access = new ArrayList<>(recipients.size());

    Map<CertificateType, Integer> typeCounts = new HashMap<>();

    for (Recipient recipient : recipients) {
      byte[]          theirUnidentifiedAccessKey       = getTargetUnidentifiedAccessKey(recipient);
      CertificateType certificateType                  = getUnidentifiedAccessCertificateType(recipient);
      byte[]          ourUnidentifiedAccessCertificate = SignalStore.certificateValues().getUnidentifiedAccessCertificate(certificateType);

      int typeCount = Util.getOrDefault(typeCounts, certificateType, 0);
      typeCount++;
      typeCounts.put(certificateType, typeCount);

      if (theirUnidentifiedAccessKey != null && ourUnidentifiedAccessCertificate != null) {
        try {
          access.add(Optional.of(new UnidentifiedAccessPair(new UnidentifiedAccess(theirUnidentifiedAccessKey,
                                                                                   ourUnidentifiedAccessCertificate),
                                                            new UnidentifiedAccess(ourUnidentifiedAccessKey,
                                                                                   ourUnidentifiedAccessCertificate))));
        } catch (InvalidCertificateException e) {
          Log.w(TAG, e);
          access.add(Optional.absent());
        }
      } else {
        access.add(Optional.absent());
      }
    }

    int unidentifiedCount = Stream.of(access).filter(Optional::isPresent).toList().size();
    int otherCount        = access.size() - unidentifiedCount;

    if (log) {
      Log.i(TAG, "Unidentified: " + unidentifiedCount + ", Other: " + otherCount + ". Types: " + typeCounts);
    }

    return access;
  }

  public static Optional<UnidentifiedAccessPair> getAccessForSync(@NonNull Context context) {
    try {
      byte[] ourUnidentifiedAccessKey         = UnidentifiedAccess.deriveAccessKeyFrom(ProfileKeyUtil.getSelfProfileKey());
      byte[] ourUnidentifiedAccessCertificate = getUnidentifiedAccessCertificate(Recipient.self());

      if (TextSecurePreferences.isUniversalUnidentifiedAccess(context)) {
        ourUnidentifiedAccessKey = Util.getSecretBytes(16);
      }

      if (ourUnidentifiedAccessCertificate != null) {
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

  private static @NonNull CertificateType getUnidentifiedAccessCertificateType(@NonNull Recipient recipient) {
    PhoneNumberPrivacyValues.PhoneNumberSharingMode sendPhoneNumberTo = SignalStore.phoneNumberPrivacy().getPhoneNumberSharingMode();

    switch (sendPhoneNumberTo) {
      case EVERYONE: return CertificateType.UUID_AND_E164;
      case CONTACTS: return recipient.isSystemContact() ? CertificateType.UUID_AND_E164 : CertificateType.UUID_ONLY;
      case NOBODY  : return CertificateType.UUID_ONLY;
      default      : throw new AssertionError();
    }
  }

  private static byte[] getUnidentifiedAccessCertificate(@NonNull Recipient recipient) {
    return SignalStore.certificateValues()
                      .getUnidentifiedAccessCertificate(getUnidentifiedAccessCertificateType(recipient));
  }

  private static @Nullable byte[] getTargetUnidentifiedAccessKey(@NonNull Recipient recipient) {
    ProfileKey theirProfileKey = ProfileKeyUtil.profileKeyOrNull(recipient.resolve().getProfileKey());

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
