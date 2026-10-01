package org.thoughtcrime.securesms.crypto;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;


import org.signal.core.util.Base64;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.signal.libsignal.metadata.certificate.SenderCertificate;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.database.RecipientTable.SealedSenderAccessMode;
import org.thoughtcrime.securesms.database.model.RecipientRecord;
import org.thoughtcrime.securesms.jobmanager.impl.SealedSenderConstraint;
import org.thoughtcrime.securesms.keyvalue.CertificateType;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;

import java.io.IOException;
import java.util.ArrayList;
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
    return SealedSenderAccess.forIndividual(getAccessFor(recipient, getSealedSenderCertificate(), log));
  }

  /**
   * Profile fetches only send the access key, so this tolerates an expired certificate and never triggers a rotation.
   */
  @WorkerThread
  public static @Nullable SealedSenderAccess getSealedSenderAccessForProfileFetch(@NonNull Recipient recipient, boolean log) {
    return SealedSenderAccess.forIndividual(getAccessFor(recipient, getStoredSenderCertificate(), log));
  }

  public static @Nullable SealedSenderAccess getSealedSenderAccessFor(@NonNull Recipient recipient, @Nullable SealedSenderAccess.CreateGroupSendToken createGroupSendToken) {
    SenderCertificate certificate = getSealedSenderCertificate();

    return SealedSenderAccess.forIndividualWithGroupFallback(getAccessFor(recipient, certificate, true), certificate, createGroupSendToken);
  }

  @WorkerThread
  public static @Nullable SealedSenderAccess getSealedSenderAccessFor(@NonNull RecipientRecord record) {
    return getSealedSenderAccessFor(record, true);
  }

  @WorkerThread
  public static @Nullable SealedSenderAccess getSealedSenderAccessFor(@NonNull RecipientRecord record, boolean log) {
    return SealedSenderAccess.forIndividual(getAccessFor(record, getSealedSenderCertificate(), log));
  }

  public static @Nullable SealedSenderAccess getSealedSenderAccessFor(@NonNull RecipientRecord record, @Nullable SealedSenderAccess.CreateGroupSendToken createGroupSendToken) {
    SenderCertificate certificate = getSealedSenderCertificate();

    return SealedSenderAccess.forIndividualWithGroupFallback(getAccessFor(record, certificate, true), certificate, createGroupSendToken);
  }

  @WorkerThread
  private static @Nullable UnidentifiedAccess getAccessFor(@NonNull Recipient recipient, @Nullable SenderCertificate certificate, boolean log) {
    return getAccessFor(Collections.singletonList(recipient), certificate, false, log)
        .get(0)
        .orElse(null);
  }

  @WorkerThread
  private static @Nullable UnidentifiedAccess getAccessFor(@NonNull RecipientRecord record, @Nullable SenderCertificate certificate, boolean log) {
    UnidentifiedAccess unidentifiedAccess = null;
    if (certificate != null) {
      try {
        unidentifiedAccess = getTargetUnidentifiedAccess(record.getProfileKey(), getEffectiveSealedSenderAccessMode(record), certificate.getSerialized(), false);
      } catch (InvalidCertificateException e) {
        Log.w(TAG, "Invalid unidentified access certificate!", e);
      }
    }

    if (log) {
      Log.i(TAG, "Unidentified: " + (unidentifiedAccess != null ? 1 : 0) + ", Other: " + (unidentifiedAccess != null ? 0 : 1));
    }

    return unidentifiedAccess;
  }

  /**
   * Mirrors {@link Recipient#getSealedSenderAccessMode()}: a recipient addressed only by PNI cannot receive sealed sender.
   */
  private static @NonNull SealedSenderAccessMode getEffectiveSealedSenderAccessMode(@NonNull RecipientRecord record) {
    if (record.getAci() == null && record.getPni() != null) {
      return SealedSenderAccessMode.DISABLED;
    } else {
      return record.getSealedSenderAccessMode();
    }
  }

  @WorkerThread
  public static Map<RecipientId, Optional<UnidentifiedAccess>> getAccessMapFor(@NonNull List<Recipient> recipients, boolean isForStory) {
    List<Optional<UnidentifiedAccess>> accessList = getAccessFor(recipients, getSealedSenderCertificate(), isForStory, true);

    Iterator<Recipient>                    recipientIterator = recipients.iterator();
    Iterator<Optional<UnidentifiedAccess>> accessIterator    = accessList.iterator();

    Map<RecipientId, Optional<UnidentifiedAccess>> accessMap = new HashMap<>(recipients.size());

    while (recipientIterator.hasNext()) {
      accessMap.put(recipientIterator.next().getId(), accessIterator.next());
    }

    return accessMap;
  }

  @WorkerThread
  private static List<Optional<UnidentifiedAccess>> getAccessFor(@NonNull List<Recipient> recipients, @Nullable SenderCertificate certificate, boolean isForStory, boolean log) {
    List<Optional<UnidentifiedAccess>> access = recipients.parallelStream().map(recipient -> {
      UnidentifiedAccess unidentifiedAccess = null;
      if (certificate != null) {
        try {
          Recipient resolved = recipient.resolve();
          unidentifiedAccess = getTargetUnidentifiedAccess(resolved.getProfileKey(), resolved.getSealedSenderAccessMode(), certificate.getSerialized(), isForStory);
        } catch (InvalidCertificateException e) {
          Log.w(TAG, "Invalid unidentified access certificate!", e);
        }
      }
      return Optional.ofNullable(unidentifiedAccess);
    }).collect(Collectors.toList());

    int unidentifiedCount = access.stream().filter(Optional::isPresent).collect(Collectors.toList()).size();
    int otherCount        = access.size() - unidentifiedCount;

    if (log) {
      Log.i(TAG, "Unidentified: " + unidentifiedCount + ", Other: " + otherCount);
    }

    return access;
  }

  private static @NonNull CertificateType getUnidentifiedAccessCertificateType() {
    if (SignalStore.account().isPhoneNumberless()) {
      return CertificateType.ACI_ONLY;
    } else if (SignalStore.phoneNumberPrivacy().isPhoneNumberSharingEnabled()) {
      return CertificateType.ACI_AND_E164;
    } else {
      return CertificateType.ACI_ONLY;
    }
  }

  private static byte[] getUnidentifiedAccessCertificate() {
    return SignalStore.certificate()
                      .getUnidentifiedAccessCertificate(getUnidentifiedAccessCertificateType());
  }

  private static @Nullable SenderCertificate getStoredSenderCertificate() {
    byte[] certificateBytes = getUnidentifiedAccessCertificate();

    if (certificateBytes == null) {
      return null;
    }

    try {
      return new SenderCertificate(certificateBytes);
    } catch (InvalidCertificateException e) {
      Log.w(TAG, "Unable to parse our unidentified access certificate!", e);
      return null;
    }
  }

  /**
   * Resolve once per access lookup: each null path re-runs {@link SealedSenderConstraint#refreshAndRotateIfNeeded()}, so calling this twice enqueues two
   * rotations.
   */
  public static @Nullable SenderCertificate getSealedSenderCertificate() {
    byte[] certificateBytes = getUnidentifiedAccessCertificate();

    if (certificateBytes == null) {
      Log.w(TAG, "Missing our unidentified access certificate!");
      SealedSenderConstraint.refreshAndRotateIfNeeded();
      return null;
    }

    SenderCertificate certificate;
    try {
      certificate = new SenderCertificate(certificateBytes);
    } catch (InvalidCertificateException e) {
      Log.w(TAG, "Unable to parse our unidentified access certificate!", e);
      SealedSenderConstraint.refreshAndRotateIfNeeded();
      return null;
    }

    long now = SignalStore.misc().getEstimatedServerTime();

    if (now >= certificate.getExpiration()) {
      Log.w(TAG, "Our unidentified access certificate expired " + (now - certificate.getExpiration()) + " ms ago! Sending without sealed sender until it rotates.");
      SealedSenderConstraint.refreshAndRotateIfNeeded();
      return null;
    }

    return certificate;
  }

  private static @Nullable UnidentifiedAccess getTargetUnidentifiedAccess(@Nullable byte[] theirProfileKeyBytes, @NonNull SealedSenderAccessMode accessMode, @NonNull byte[] certificate, boolean isForStory) throws InvalidCertificateException {
    ProfileKey theirProfileKey = ProfileKeyUtil.profileKeyOrNull(theirProfileKeyBytes);

    byte[] accessKey;

    switch (accessMode) {
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
        throw new AssertionError("Unknown mode: " + accessMode.getMode());
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
        String[]               base64Strings = BuildConfig.UNIDENTIFIED_SENDER_TRUST_ROOTS;
        ArrayList<ECPublicKey> roots         = new ArrayList<>(base64Strings.length);

        for (String base64String: base64Strings) {
          ECPublicKey unidentifiedSenderTrustRoot = new ECPublicKey(Base64.decode(base64String));
          roots.add(unidentifiedSenderTrustRoot);
        }

        return new CertificateValidator(roots);
      } catch (InvalidKeyException | IOException e) {
        throw new AssertionError(e);
      }
    }
  }
}
