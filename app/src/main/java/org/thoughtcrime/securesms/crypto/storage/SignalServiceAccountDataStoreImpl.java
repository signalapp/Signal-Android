package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.groups.state.SenderKeyRecord;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.SignalServiceAccountDataStore;
import org.whispersystems.signalservice.api.push.DistributionId;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SignalServiceAccountDataStoreImpl implements SignalServiceAccountDataStore {

  private final Context                    context;
  private final TextSecurePreKeyStore      preKeyStore;
  private final TextSecurePreKeyStore      signedPreKeyStore;
  private final TextSecureIdentityKeyStore identityKeyStore;
  private final TextSecureSessionStore     sessionStore;
  private final SignalSenderKeyStore       senderKeyStore;

  public SignalServiceAccountDataStoreImpl(@NonNull Context context,
                                           @NonNull TextSecurePreKeyStore preKeyStore,
                                           @NonNull TextSecureIdentityKeyStore identityKeyStore,
                                           @NonNull TextSecureSessionStore sessionStore,
                                           @NonNull SignalSenderKeyStore senderKeyStore)
  {
    this.context           = context;
    this.preKeyStore       = preKeyStore;
    this.signedPreKeyStore = preKeyStore;
    this.identityKeyStore  = identityKeyStore;
    this.sessionStore      = sessionStore;
    this.senderKeyStore    = senderKeyStore;
  }

  @Override
  public boolean isMultiDevice() {
    return TextSecurePreferences.isMultiDevice(context);
  }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
    return identityKeyStore.getIdentityKeyPair();
  }

  @Override
  public int getLocalRegistrationId() {
    return identityKeyStore.getLocalRegistrationId();
  }

  @Override
  public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
    return identityKeyStore.saveIdentity(address, identityKey);
  }

  @Override
  public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
    return identityKeyStore.isTrustedIdentity(address, identityKey, direction);
  }

  @Override
  public IdentityKey getIdentity(SignalProtocolAddress address) {
    return identityKeyStore.getIdentity(address);
  }

  @Override
  public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
    return preKeyStore.loadPreKey(preKeyId);
  }

  @Override
  public void storePreKey(int preKeyId, PreKeyRecord record) {
    preKeyStore.storePreKey(preKeyId, record);
  }

  @Override
  public boolean containsPreKey(int preKeyId) {
    return preKeyStore.containsPreKey(preKeyId);
  }

  @Override
  public void removePreKey(int preKeyId) {
    preKeyStore.removePreKey(preKeyId);
  }

  @Override
  public SessionRecord loadSession(SignalProtocolAddress axolotlAddress) {
    return sessionStore.loadSession(axolotlAddress);
  }

  @Override
  public List<SessionRecord> loadExistingSessions(List<SignalProtocolAddress> addresses) throws NoSessionException {
    return sessionStore.loadExistingSessions(addresses);
  }

  @Override
  public List<Integer> getSubDeviceSessions(String number) {
    return sessionStore.getSubDeviceSessions(number);
  }

  @Override
  public Set<SignalProtocolAddress> getAllAddressesWithActiveSessions(List<String> addressNames) {
    return sessionStore.getAllAddressesWithActiveSessions(addressNames);
  }

  @Override
  public void storeSession(SignalProtocolAddress axolotlAddress, SessionRecord record) {
    sessionStore.storeSession(axolotlAddress, record);
  }

  @Override
  public boolean containsSession(SignalProtocolAddress axolotlAddress) {
    return sessionStore.containsSession(axolotlAddress);
  }

  @Override
  public void deleteSession(SignalProtocolAddress axolotlAddress) {
    sessionStore.deleteSession(axolotlAddress);
  }

  @Override
  public void deleteAllSessions(String number) {
    sessionStore.deleteAllSessions(number);
  }

  @Override
  public void archiveSession(SignalProtocolAddress address) {
    sessionStore.archiveSession(address);
    senderKeyStore.clearSenderKeySharedWith(Collections.singleton(address));
  }

  @Override
  public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
    return signedPreKeyStore.loadSignedPreKey(signedPreKeyId);
  }

  @Override
  public List<SignedPreKeyRecord> loadSignedPreKeys() {
    return signedPreKeyStore.loadSignedPreKeys();
  }

  @Override
  public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
    signedPreKeyStore.storeSignedPreKey(signedPreKeyId, record);
  }

  @Override
  public boolean containsSignedPreKey(int signedPreKeyId) {
    return signedPreKeyStore.containsSignedPreKey(signedPreKeyId);
  }

  @Override
  public void removeSignedPreKey(int signedPreKeyId) {
    signedPreKeyStore.removeSignedPreKey(signedPreKeyId);
  }

  @Override
  public void storeSenderKey(SignalProtocolAddress sender, UUID distributionId, SenderKeyRecord record) {
    senderKeyStore.storeSenderKey(sender, distributionId, record);
  }

  @Override
  public SenderKeyRecord loadSenderKey(SignalProtocolAddress sender, UUID distributionId) {
    return senderKeyStore.loadSenderKey(sender, distributionId);
  }

  @Override
  public Set<SignalProtocolAddress> getSenderKeySharedWith(DistributionId distributionId) {
    return senderKeyStore.getSenderKeySharedWith(distributionId);
  }

  @Override
  public void markSenderKeySharedWith(DistributionId distributionId, Collection<SignalProtocolAddress> addresses) {
    senderKeyStore.markSenderKeySharedWith(distributionId, addresses);
  }

  @Override
  public void clearSenderKeySharedWith(Collection<SignalProtocolAddress> addresses) {
    senderKeyStore.clearSenderKeySharedWith(addresses);
  }

  public @NonNull TextSecureIdentityKeyStore identities() {
    return identityKeyStore;
  }

  public @NonNull TextSecurePreKeyStore preKeys() {
    return preKeyStore;
  }

  public @NonNull TextSecureSessionStore sessions() {
    return sessionStore;
  }

  public @NonNull SignalSenderKeyStore senderKeys() {
    return senderKeyStore;
  }

}
