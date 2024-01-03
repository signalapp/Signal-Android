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
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.signal.core.util.Base64;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccessPair;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    final byte[] ourUnidentifiedAccessKey;

    if (TextSecurePreferences.isUniversalUnidentifiedAccess(context)) {
      ourUnidentifiedAccessKey = UNRESTRICTED_KEY;
    } else {
      ourUnidentifiedAccessKey = ProfileKeyUtil.getSelfProfileKey().deriveAccessKey();
    }

    CertificateType certificateType                  = getUnidentifiedAccessCertificateType();
    byte[]          ourUnidentifiedAccessCertificate = SignalStore.certificateValues().getUnidentifiedAccessCertificate(certificateType);

    List<Optional<UnidentifiedAccessPair>> access = recipients.parallelStream().map(recipient -> {
      UnidentifiedAccessPair unidentifiedAccessPair = null;
      if (ourUnidentifiedAccessCertificate != null) {
        try {
          UnidentifiedAccess theirAccess = getTargetUnidentifiedAccess(recipient, ourUnidentifiedAccessCertificate, isForStory);
          UnidentifiedAccess ourAccess   = new UnidentifiedAccess(ourUnidentifiedAccessKey, ourUnidentifiedAccessCertificate, false);

          if (theirAccess != null) {
            unidentifiedAccessPair = new UnidentifiedAccessPair(theirAccess, ourAccess);
          }
        } catch (InvalidCertificateException e) {
          Log.w(TAG, "Invalid unidentified access certificate!", e);
        }
      } else {
        Log.w(TAG, "Missing unidentified access certificate!");
      }
      return Optional.ofNullable(unidentifiedAccessPair);
    }).collect(Collectors.toList());

    int unidentifiedCount = Stream.of(access).filter(Optional::isPresent).toList().size();
    int otherCount        = access.size() - unidentifiedCount;

    if (log) {
      Log.i(TAG, "Unidentified: " + unidentifiedCount + ", Other: " + otherCount);
    }

    return access;
  }

  public static Optional<UnidentifiedAccessPair> getAccessForSync(@NonNull Context context) {
    try {
      byte[] ourUnidentifiedAccessKey         = UnidentifiedAccess.deriveAccessKeyFrom(ProfileKeyUtil.getSelfProfileKey());
      byte[] ourUnidentifiedAccessCertificate = getUnidentifiedAccessCertificate();

      if (TextSecurePreferences.isUniversalUnidentifiedAccess(context)) {
        ourUnidentifiedAccessKey = UNRESTRICTED_KEY;
      }

      if (ourUnidentifiedAccessCertificate != null) {
        return Optional.of(new UnidentifiedAccessPair(new UnidentifiedAccess(ourUnidentifiedAccessKey,
                                                                             ourUnidentifiedAccessCertificate,
                                                                             false),
                                                      new UnidentifiedAccess(ourUnidentifiedAccessKey,
                                                                             ourUnidentifiedAccessCertificate,
                                                                             false)));
      }

      return Optional.empty();
    } catch (InvalidCertificateException e) {
      Log.w(TAG, e);
      return Optional.empty();
    }
  }

  private static @NonNull CertificateType getUnidentifiedAccessCertificateType() {
    if (SignalStore.phoneNumberPrivacy().isPhoneNumberSharingEnabled()) {
      return CertificateType.ACI_AND_E164;
    } else {
      return CertificateType.ACI_ONLY;
    }
  }

  private static byte[] getUnidentifiedAccessCertificate() {
    return SignalStore.certificateValues()
                      .getUnidentifiedAccessCertificate(getUnidentifiedAccessCertificateType());
  }

  private static @Nullable UnidentifiedAccess getTargetUnidentifiedAccess(@NonNull Recipient recipient, @NonNull byte[] certificate, boolean isForStory) throws InvalidCertificateException {
    ProfileKey theirProfileKey = ProfileKeyUtil.profileKeyOrNull(recipient.resolve().getProfileKey());

    byte[] accessKey;

    switch (recipient.resolve().getUnidentifiedAccessMode()) {
      case UNKNOWN:
        if (theirProfileKey == null) {
          if (isForStory) {
            accessKey = null;
          } else {
            accessKey = UNRESTRICTED_KEY;
          }
        } else {
          accessKey = theirProfileKey.deriveAccessKey();
        }
        break;
      case DISABLED:
        accessKey = null;
        break;
      case ENABLED:
        if (theirProfileKey == null) {
          accessKey = null;
        } else {
          accessKey = theirProfileKey.deriveAccessKey();
        }
        break;
      case UNRESTRICTED:
        accessKey = UNRESTRICTED_KEY;
        break;
      default:
        throw new AssertionError("Unknown mode: " + recipient.getUnidentifiedAccessMode().getMode());
    }

    if (accessKey == null && isForStory) {
      return new UnidentifiedAccess(UNRESTRICTED_KEY, certificate, true);
    } else if (accessKey != null) {
      return new UnidentifiedAccess(accessKey, certificate, false);
    } else {
      return null;
    }
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
