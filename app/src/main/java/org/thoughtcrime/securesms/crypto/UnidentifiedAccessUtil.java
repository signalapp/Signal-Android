package org.thoughtcrime.securesms.crypto;


import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.keyvalue.CertificateType;
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UnidentifiedAccessUtil {

  private static final String TAG = Log.tag(UnidentifiedAccessUtil.class);

  private static final byte[] UNRESTRICTED_KEY = new byte[16];

  public static CertificateValidator getCertificateValidator() {
    return CertificateValidatorHolder.INSTANCE.certificateValidator;
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
  public static Map<RecipientId, Optional<UnidentifiedAccessPair>> getAccessMapFor(@NonNull Context context, @NonNull List<Recipient> recipients, boolean isForStory) {
    List<Optional<UnidentifiedAccessPair>> accessList = getAccessFor(context, recipients, isForStory, true);

    Iterator<Recipient>                        recipientIterator = recipients.iterator();
    Iterator<Optional<UnidentifiedAccessPair>> accessIterator    = accessList.iterator();

    Map<RecipientId, Optional<UnidentifiedAccessPair>> accessMap = new HashMap<>(recipients.size());

    while (recipientIterator.hasNext()) {
      accessMap.put(recipientIterator.next().getId(), accessIterator.next());
    }

    return accessMap;
  }
  
  @WorkerThread
  public static List<Optional<UnidentifiedAccessPair>> getAccessFor(@NonNull Context context, @NonNull List<Recipient> recipients, boolean log) {
    return getAccessFor(context, recipients, false, log);
  }

  @WorkerThread
  public static List<Optional<UnidentifiedAccessPair>> getAccessFor(@NonNull Context context, @NonNull List<Recipient> recipients, boolean isForStory, boolean log) {
    byte[] ourUnidentifiedAccessKey = UnidentifiedAccess.deriveAccessKeyFrom(ProfileKeyUtil.getSelfProfileKey());

    if (TextSecurePreferences.isUniversalUnidentifiedAccess(context)) {
      ourUnidentifiedAccessKey = UNRESTRICTED_KEY;
    }

    List<Optional<UnidentifiedAccessPair>> access = new ArrayList<>(recipients.size());

    Map<CertificateType, Integer> typeCounts = new HashMap<>();

    for (Recipient recipient : recipients) {
      byte[]          theirUnidentifiedAccessKey       = getTargetUnidentifiedAccessKey(recipient, isForStory);
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
          access.add(Optional.empty());
        }
      } else {
        access.add(Optional.empty());
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
        ourUnidentifiedAccessKey = UNRESTRICTED_KEY;
      }

      if (ourUnidentifiedAccessCertificate != null) {
        return Optional.of(new UnidentifiedAccessPair(new UnidentifiedAccess(ourUnidentifiedAccessKey,
                                                                             ourUnidentifiedAccessCertificate),
                                                      new UnidentifiedAccess(ourUnidentifiedAccessKey,
                                                                             ourUnidentifiedAccessCertificate)));
      }

      return Optional.empty();
    } catch (InvalidCertificateException e) {
      Log.w(TAG, e);
      return Optional.empty();
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

  private static @Nullable byte[] getTargetUnidentifiedAccessKey(@NonNull Recipient recipient, boolean isForStory) {
    ProfileKey theirProfileKey = ProfileKeyUtil.profileKeyOrNull(recipient.resolve().getProfileKey());

    byte[] accessKey;

    switch (recipient.resolve().getUnidentifiedAccessMode()) {
      case UNKNOWN:
        if (theirProfileKey == null) {
          accessKey = UNRESTRICTED_KEY;
        } else {
          accessKey = UnidentifiedAccess.deriveAccessKeyFrom(theirProfileKey);
        }
        break;
      case DISABLED:
        accessKey = null;
        break;
      case ENABLED:
        if (theirProfileKey == null) {
          accessKey = null;
        } else {
          accessKey = UnidentifiedAccess.deriveAccessKeyFrom(theirProfileKey);
        }
        break;
      case UNRESTRICTED:
        accessKey = UNRESTRICTED_KEY;
        break;
      default:
        throw new AssertionError("Unknown mode: " + recipient.getUnidentifiedAccessMode().getMode());
    }

    if (accessKey == null && isForStory) {
      accessKey = UNRESTRICTED_KEY;
    }

    return accessKey;
  }

  private enum CertificateValidatorHolder {
    INSTANCE;

    final CertificateValidator certificateValidator = buildCertificateValidator();

    private static CertificateValidator buildCertificateValidator() {
      try {
        ECPublicKey unidentifiedSenderTrustRoot = Curve.decodePoint(Base64.decode(BuildConfig.UNIDENTIFIED_SENDER_TRUST_ROOT), 0);
        return new CertificateValidator(unidentifiedSenderTrustRoot);
      } catch (InvalidKeyException | IOException e) {
        throw new AssertionError(e);
      }
    }
  }
}
