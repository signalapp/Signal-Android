package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase.IdentityRecord;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
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

  private static final String TAG = TextSecureIdentityKeyStore.class.getSimpleName();
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

  public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey,
                              boolean blockingApproval, boolean nonBlockingApproval)
  {
    synchronized (LOCK) {
      IdentityDatabase         identityDatabase = DatabaseFactory.getIdentityDatabase(context);
      Recipients               recipients       = RecipientFactory.getRecipientsFromString(context, address.getName(), true);
      long                     recipientId      = recipients.getPrimaryRecipient().getRecipientId();
      Optional<IdentityRecord> identityRecord   = identityDatabase.getIdentity(recipientId);

      if (!identityRecord.isPresent()) {
        Log.w(TAG, "Saving new identity...");
        identityDatabase.saveIdentity(recipientId, identityKey, true, System.currentTimeMillis(), blockingApproval, nonBlockingApproval);
        return false;
      }

      if (!identityRecord.get().getIdentityKey().equals(identityKey)) {
        Log.w(TAG, "Replacing existing identity...");
        identityDatabase.saveIdentity(recipientId, identityKey, false, System.currentTimeMillis(), blockingApproval, nonBlockingApproval);
        IdentityUtil.markIdentityUpdate(context, recipients.getPrimaryRecipient());
        return true;
      }

      if (isBlockingApprovalRequired(identityRecord.get()) || isNonBlockingApprovalRequired(identityRecord.get())) {
        Log.w(TAG, "Setting approval status...");
        identityDatabase.setApproval(recipientId, blockingApproval, nonBlockingApproval);
        return false;
      }

      return false;
    }
  }

  @Override
  public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
    return saveIdentity(address, identityKey, !TextSecurePreferences.isSendingIdentityApprovalRequired(context), false);
  }

  @Override
  public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
    synchronized (LOCK) {
      IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(context);
      long             recipientId      = RecipientFactory.getRecipientsFromString(context, address.getName(), true).getPrimaryRecipient().getRecipientId();
      String           ourNumber        = TextSecurePreferences.getLocalNumber(context);
      long             ourRecipientId   = RecipientFactory.getRecipientsFromString(context, ourNumber, true).getPrimaryRecipient().getRecipientId();

      if (ourRecipientId == recipientId || ourNumber.equals(address.getName())) {
        return identityKey.equals(IdentityKeyUtil.getIdentityKey(context));
      }

      switch (direction) {
        case SENDING:   return isTrustedForSending(identityKey, identityDatabase.getIdentity(recipientId));
        case RECEIVING: return true;
        default:        throw new AssertionError("Unknown direction: " + direction);
      }
    }
  }

  private boolean isTrustedForSending(IdentityKey identityKey, Optional<IdentityRecord> identityRecord) {
    if (!identityRecord.isPresent()) {
      Log.w(TAG, "Nothing here, returning true...");
      return true;
    }

    if (!identityKey.equals(identityRecord.get().getIdentityKey())) {
      Log.w(TAG, "Identity keys don't match...");
      return false;
    }

    if (isBlockingApprovalRequired(identityRecord.get())) {
      Log.w(TAG, "Needs blocking approval!");
      return false;
    }

    if (isNonBlockingApprovalRequired(identityRecord.get())) {
      Log.w(TAG, "Needs non-blocking approval!");
      return false;
    }

    return true;
  }

  private boolean isBlockingApprovalRequired(IdentityRecord identityRecord) {
    return !identityRecord.isFirstUse() &&
           TextSecurePreferences.isSendingIdentityApprovalRequired(context) &&
           !identityRecord.isApprovedBlocking();
  }

  private boolean isNonBlockingApprovalRequired(IdentityRecord identityRecord) {
    return !identityRecord.isFirstUse() &&
           System.currentTimeMillis() - identityRecord.getTimestamp() < TimeUnit.SECONDS.toMillis(TIMESTAMP_THRESHOLD_SECONDS) &&
           !identityRecord.isApprovedNonBlocking();
  }
}
