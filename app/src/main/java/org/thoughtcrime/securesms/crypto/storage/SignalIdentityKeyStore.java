package org.thoughtcrime.securesms.crypto.storage;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.IdentityDatabase.VerifiedStatus;
import org.thoughtcrime.securesms.database.identity.IdentityRecordList;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.IdentityKeyStore;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;
import java.util.function.Supplier;

/**
 * A wrapper around an instance of {@link SignalBaseIdentityKeyStore} that lets us report different values for {@link #getIdentityKeyPair()}.
 * This lets us have multiple instances (one for ACI, one for PNI) that share the same underlying data while also reporting the correct identity key.
 */
public class SignalIdentityKeyStore implements IdentityKeyStore {

  private final SignalBaseIdentityKeyStore baseStore;
  private final Supplier<IdentityKeyPair>  identitySupplier;

  public SignalIdentityKeyStore(@NonNull SignalBaseIdentityKeyStore baseStore, @NonNull Supplier<IdentityKeyPair> identitySupplier) {
    this.baseStore        = baseStore;
    this.identitySupplier = identitySupplier;
  }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    return identitySupplier.get();
  }

  @Override
  public int getLocalRegistrationId() {
    return baseStore.getLocalRegistrationId();
  }

  @Override
  public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
    return baseStore.saveIdentity(address, identityKey);
  }

  public @NonNull SaveResult saveIdentity(SignalProtocolAddress address, IdentityKey identityKey, boolean nonBlockingApproval) {
    return baseStore.saveIdentity(address, identityKey, nonBlockingApproval);
  }

  public void saveIdentityWithoutSideEffects(@NonNull RecipientId recipientId,
                                             IdentityKey identityKey,
                                             VerifiedStatus verifiedStatus,
                                             boolean firstUse,
                                             long timestamp,
                                             boolean nonBlockingApproval)
  {
    baseStore.saveIdentityWithoutSideEffects(recipientId, identityKey, verifiedStatus, firstUse, timestamp, nonBlockingApproval);
  }

  @Override
  public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
    return baseStore.isTrustedIdentity(address, identityKey, direction);
  }

  @Override
  public IdentityKey getIdentity(SignalProtocolAddress address) {
    return baseStore.getIdentity(address);
  }

  public @NonNull Optional<IdentityRecord> getIdentityRecord(@NonNull RecipientId recipientId) {
    return baseStore.getIdentityRecord(recipientId);
  }

  public @NonNull IdentityRecordList getIdentityRecords(@NonNull List<Recipient> recipients) {
    return baseStore.getIdentityRecords(recipients);
  }

  public void setApproval(@NonNull RecipientId recipientId, boolean nonBlockingApproval) {
    baseStore.setApproval(recipientId, nonBlockingApproval);
  }

  public void setVerified(@NonNull RecipientId recipientId, IdentityKey identityKey, VerifiedStatus verifiedStatus) {
    baseStore.setVerified(recipientId, identityKey, verifiedStatus);
  }

  public void delete(@NonNull String addressName) {
    baseStore.delete(addressName);
  }

  public void invalidate(@NonNull String addressName) {
    baseStore.invalidate(addressName);
  }

  public enum SaveResult {
    NEW,
    UPDATE,
    NON_BLOCKING_APPROVAL_REQUIRED,
    NO_CHANGE
  }
}
