package org.thoughtcrime.securesms.crypto;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.signal.core.util.Base64;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.signal.libsignal.metadata.certificate.SenderCertificate;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.keyvalue.CertificateType;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SealedSenderAccessUtil {

  private static final String TAG = Log.tag(SealedSenderAccessUtil.class);

  private static final byte[] UNRESTRICTED_KEY = new byte[16];

  public static CertificateValidator getCertificateValidator() {
    return CertificateValidatorHolder.INSTANCE.certificateValidator;
  }

  @WorkerThread
  public static @Nullable SealedSenderAccess getSealedSenderAccessFor(@NonNull Recipient recipient) {
    return getSealedSenderAccessFor(recipient, true);
  }

  @WorkerThread
  public static @Nullable SealedSenderAccess getSealedSenderAccessFor(@NonNull Recipient recipient, boolean log) {
    return SealedSenderAccess.forIndividual(getAccessFor(recipient, log));
  }

  public static @Nullable SealedSenderAccess getSealedSenderAccessFor(@NonNull Recipient recipient, @Nullable SealedSenderAccess.CreateGroupSendToken createGroupSendToken) {
    return SealedSenderAccess.forIndividualWithGroupFallback(getAccessFor(recipient, true), getSealedSenderCertificate(), createGroupSendToken);
  }

  @WorkerThread
  private static @Nullable UnidentifiedAccess getAccessFor(@NonNull Recipient recipient, boolean log) {
    return getAccessFor(Collections.singletonList(recipient), false, log)
        .get(0)
        .orElse(null);
  }

  @WorkerThread
  public static Map<RecipientId, Optional<UnidentifiedAccess>> getAccessMapFor(@NonNull List<Recipient> recipients, boolean isForStory) {
    List<Optional<UnidentifiedAccess>> accessList = getAccessFor(recipients, isForStory, true);

    Iterator<Recipient>                    recipientIterator = recipients.iterator();
    Iterator<Optional<UnidentifiedAccess>> accessIterator    = accessList.iterator();

    Map<RecipientId, Optional<UnidentifiedAccess>> accessMap = new HashMap<>(recipients.size());

    while (recipientIterator.hasNext()) {
      accessMap.put(recipientIterator.next().getId(), accessIterator.next());
    }

    return accessMap;
  }

  @WorkerThread
  private static List<Optional<UnidentifiedAccess>> getAccessFor(@NonNull List<Recipient> recipients, boolean isForStory, boolean log) {
    CertificateType certificateType                  = getUnidentifiedAccessCertificateType();
    byte[]          ourUnidentifiedAccessCertificate = SignalStore.certificate().getUnidentifiedAccessCertificate(certificateType);

    List<Optional<UnidentifiedAccess>> access = recipients.parallelStream().map(recipient -> {
      UnidentifiedAccess unidentifiedAccess = null;
      if (ourUnidentifiedAccessCertificate != null) {
        try {
          unidentifiedAccess = getTargetUnidentifiedAccess(recipient, ourUnidentifiedAccessCertificate, isForStory);
        } catch (InvalidCertificateException e) {
          Log.w(TAG, "Invalid unidentified access certificate!", e);
        }
      } else {
        Log.w(TAG, "Missing our unidentified access certificate!");
      }
      return Optional.ofNullable(unidentifiedAccess);
    }).collect(Collectors.toList());

    int unidentifiedCount = Stream.of(access).filter(Optional::isPresent).toList().size();
    int otherCount        = access.size() - unidentifiedCount;

    if (log) {
      Log.i(TAG, "Unidentified: " + unidentifiedCount + ", Other: " + otherCount);
    }

    return access;
  }

  public static @Nullable SenderCertificate getSealedSenderCertificate() {
    byte[] unidentifiedAccessCertificate = getUnidentifiedAccessCertificate();
    if (unidentifiedAccessCertificate == null) {
      return null;
    }

    try {
      return new SenderCertificate(unidentifiedAccessCertificate);
    } catch (InvalidCertificateException e) {
      Log.w(TAG, e);
      return null;
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
    return SignalStore.certificate()
                      .getUnidentifiedAccessCertificate(getUnidentifiedAccessCertificateType());
  }

  private static @Nullable UnidentifiedAccess getTargetUnidentifiedAccess(@NonNull Recipient recipient, @NonNull byte[] certificate, boolean isForStory) throws InvalidCertificateException {
    ProfileKey theirProfileKey = ProfileKeyUtil.profileKeyOrNull(recipient.resolve().getProfileKey());

    byte[] accessKey;

    switch (recipient.resolve().getSealedSenderAccessMode()) {
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
        throw new AssertionError("Unknown mode: " + recipient.getSealedSenderAccessMode().getMode());
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
