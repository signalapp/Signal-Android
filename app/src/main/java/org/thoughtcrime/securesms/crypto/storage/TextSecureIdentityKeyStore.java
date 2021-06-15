package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.SessionUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase.IdentityRecord;
import org.thoughtcrime.securesms.database.IdentityDatabase.VerifiedStatus;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.IdentityKeyStore;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.concurrent.TimeUnit;

public class TextSecureIdentityKeyStore implements IdentityKeyStore {

  private static final int TIMESTAMP_THRESHOLD_SECONDS = 5;

  private static final String TAG = Log.tag(TextSecureIdentityKeyStore.class);
  private static final Object LOCK = new Object();

  private final Context context;

  public TextSecureIdentityKeyStore(Context context) {
    this.context = context;
  }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    return IdentityKeyUtil.getIdentityKeyPair(context);
  }

  @Override
  public int getLocalRegistrationId() {
    return TextSecurePreferences.getLocalRegistrationId(context);
  }

  public @NonNull SaveResult saveIdentity(SignalProtocolAddress address, IdentityKey identityKey, boolean nonBlockingApproval) {
    synchronized (LOCK) {
      IdentityDatabase         identityDatabase = DatabaseFactory.getIdentityDatabase(context);
      RecipientId              recipientId      = RecipientId.fromExternalPush(address.getName());
      Optional<IdentityRecord> identityRecord   = identityDatabase.getIdentity(recipientId);

      if (!identityRecord.isPresent()) {
        Log.i(TAG, "Saving new identity...");
        identityDatabase.saveIdentity(recipientId, identityKey, VerifiedStatus.DEFAULT, true, System.currentTimeMillis(), nonBlockingApproval);
        return SaveResult.NEW;
      }

      if (!identityRecord.get().getIdentityKey().equals(identityKey)) {
        Log.i(TAG, "Replacing existing identity... Existing: " + identityRecord.get().getIdentityKey().hashCode() + " New: " + identityKey.hashCode());
        VerifiedStatus verifiedStatus;

        if (identityRecord.get().getVerifiedStatus() == VerifiedStatus.VERIFIED ||
            identityRecord.get().getVerifiedStatus() == VerifiedStatus.UNVERIFIED)
        {
          verifiedStatus = VerifiedStatus.UNVERIFIED;
        } else {
          verifiedStatus = VerifiedStatus.DEFAULT;
        }

        identityDatabase.saveIdentity(recipientId, identityKey, verifiedStatus, false, System.currentTimeMillis(), nonBlockingApproval);
        IdentityUtil.markIdentityUpdate(context, recipientId);
        SessionUtil.archiveSiblingSessions(context, address);
        DatabaseFactory.getSenderKeySharedDatabase(context).deleteAllFor(recipientId);
        return SaveResult.UPDATE;
      }

      if (isNonBlockingApprovalRequired(identityRecord.get())) {
        Log.i(TAG, "Setting approval status...");
        identityDatabase.setApproval(recipientId, nonBlockingApproval);
        return SaveResult.NON_BLOCKING_APPROVAL_REQUIRED;
      }

      return SaveResult.NO_CHANGE;
    }
  }

  @Override
  public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
    return saveIdentity(address, identityKey, false) == SaveResult.UPDATE;
  }

  @Override
  public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
    synchronized (LOCK) {
      if (DatabaseFactory.getRecipientDatabase(context).containsPhoneOrUuid(address.getName())) {
        IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(context);
        RecipientId      ourRecipientId   = Recipient.self().getId();
        RecipientId      theirRecipientId = RecipientId.fromExternalPush(address.getName());

        if (ourRecipientId.equals(theirRecipientId)) {
          return identityKey.equals(IdentityKeyUtil.getIdentityKey(context));
        }

        switch (direction) {
          case SENDING:   return isTrustedForSending(identityKey, identityDatabase.getIdentity(theirRecipientId));
          case RECEIVING: return true;
          default:        throw new AssertionError("Unknown direction: " + direction);
        }
      } else {
        Log.w(TAG, "Tried to check if identity is trusted for " + address.getName() + ", but no matching recipient existed!");
        switch (direction) {
          case SENDING:   return false;
          case RECEIVING: return true;
          default:        throw new AssertionError("Unknown direction: " + direction);
        }
      }
    }
  }

  @Override
  public IdentityKey getIdentity(SignalProtocolAddress address) {
    if (DatabaseFactory.getRecipientDatabase(context).containsPhoneOrUuid(address.getName())) {
      RecipientId              recipientId = RecipientId.fromExternalPush(address.getName());
      Optional<IdentityRecord> record      = DatabaseFactory.getIdentityDatabase(context).getIdentity(recipientId);

      if (record.isPresent()) {
        return record.get().getIdentityKey();
      } else {
        return null;
      }
    } else {
      Log.w(TAG, "Tried to get identity for " + address.getName() + ", but no matching recipient existed!");
      return null;
    }
  }

  private boolean isTrustedForSending(IdentityKey identityKey, Optional<IdentityRecord> identityRecord) {
    if (!identityRecord.isPresent()) {
      Log.w(TAG, "Nothing here, returning true...");
      return true;
    }

    if (!identityKey.equals(identityRecord.get().getIdentityKey())) {
      Log.w(TAG, "Identity keys don't match... service: " + identityKey.hashCode() + " database: " + identityRecord.get().getIdentityKey().hashCode());
      return false;
    }

    if (identityRecord.get().getVerifiedStatus() == VerifiedStatus.UNVERIFIED) {
      Log.w(TAG, "Needs unverified approval!");
      return false;
    }

    if (isNonBlockingApprovalRequired(identityRecord.get())) {
      Log.w(TAG, "Needs non-blocking approval!");
      return false;
    }

    return true;
  }

  private boolean isNonBlockingApprovalRequired(IdentityRecord identityRecord) {
    return !identityRecord.isFirstUse() &&
           System.currentTimeMillis() - identityRecord.getTimestamp() < TimeUnit.SECONDS.toMillis(TIMESTAMP_THRESHOLD_SECONDS) &&
           !identityRecord.isApprovedNonBlocking();
  }

  public enum SaveResult {
    NEW,
    UPDATE,
    NON_BLOCKING_APPROVAL_REQUIRED,
    NO_CHANGE
  }
}
