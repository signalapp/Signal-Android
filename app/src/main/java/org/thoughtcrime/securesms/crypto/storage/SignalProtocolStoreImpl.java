package org.thoughtcrime.securesms.crypto.storage;

import android.content.Context;

import org.session.libsignal.libsignal.IdentityKey;
import org.session.libsignal.libsignal.IdentityKeyPair;
import org.session.libsignal.libsignal.InvalidKeyIdException;
import org.session.libsignal.libsignal.SignalProtocolAddress;
import org.session.libsignal.libsignal.state.IdentityKeyStore;
import org.session.libsignal.libsignal.state.PreKeyRecord;
import org.session.libsignal.libsignal.state.PreKeyStore;
import org.session.libsignal.libsignal.state.SessionRecord;
import org.session.libsignal.libsignal.state.SessionStore;
import org.session.libsignal.libsignal.state.SignalProtocolStore;
import org.session.libsignal.libsignal.state.SignedPreKeyRecord;
import org.session.libsignal.libsignal.state.SignedPreKeyStore;

import java.util.List;

public class SignalProtocolStoreImpl implements SignalProtocolStore {

//  private final PreKeyStore       preKeyStore;
//  private final SignedPreKeyStore signedPreKeyStore;
//  private final IdentityKeyStore  identityKeyStore;
  private final SessionStore      sessionStore;

  public SignalProtocolStoreImpl(Context context) {
//    this.preKeyStore       = new TextSecurePreKeyStore(context);
//    this.signedPreKeyStore = new TextSecurePreKeyStore(context);
//    this.identityKeyStore  = new TextSecureIdentityKeyStore(context);
    this.sessionStore      = new TextSecureSessionStore(context);
  }

  @Override
  public IdentityKeyPair getIdentityKeyPair() {
//    return identityKeyStore.getIdentityKeyPair();
    throw new UnsupportedOperationException("This method will be removed with refactor.");
  }

  @Override
  public int getLocalRegistrationId() {
//    return identityKeyStore.getLocalRegistrationId();
    throw new UnsupportedOperationException("This method will be removed with refactor.");
  }

  @Override
  public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
//    return identityKeyStore.saveIdentity(address, identityKey);
    throw new UnsupportedOperationException("This method will be removed with refactor.");
  }

  @Override
  public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
//    return identityKeyStore.isTrustedIdentity(address, identityKey, direction);
    throw new UnsupportedOperationException("This method will be removed with refactor.");
  }

  @Override
  public IdentityKey getIdentity(SignalProtocolAddress address) {
//    return identityKeyStore.getIdentity(address);
    throw new UnsupportedOperationException("This method will be removed with refactor.");
  }

  @Override
  public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
//    return preKeyStore.loadPreKey(preKeyId);
    throw new UnsupportedOperationException("This method will be removed with refactor.");
  }

  @Override
  public void storePreKey(int preKeyId, PreKeyRecord record) {
//    preKeyStore.storePreKey(preKeyId, record);
    throw new UnsupportedOperationException("This method will be removed with refactor.");
  }

  @Override
  public boolean containsPreKey(int preKeyId) {
//    return preKeyStore.containsPreKey(preKeyId);
    throw new UnsupportedOperationException("This method will be removed with refactor.");
  }

  @Override
  public void removePreKey(int preKeyId) {
//    preKeyStore.removePreKey(preKeyId);
    throw new UnsupportedOperationException("This method will be removed with refactor.");
  }

  @Override
  public SessionRecord loadSession(SignalProtocolAddress axolotlAddress) {
    return sessionStore.loadSession(axolotlAddress);
  }

  @Override
  public List<Integer> getSubDeviceSessions(String number) {
    return sessionStore.getSubDeviceSessions(number);
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
  public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
    throw new UnsupportedOperationException("This method will be removed with refactor.");
//    return signedPreKeyStore.loadSignedPreKey(signedPreKeyId);
  }

  @Override
  public List<SignedPreKeyRecord> loadSignedPreKeys() {
    throw new UnsupportedOperationException("This method will be removed with refactor.");
//    return signedPreKeyStore.loadSignedPreKeys();
  }

  @Override
  public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
    throw new UnsupportedOperationException("This method will be removed with refactor.");
//    signedPreKeyStore.storeSignedPreKey(signedPreKeyId, record);
  }

  @Override
  public boolean containsSignedPreKey(int signedPreKeyId) {
    throw new UnsupportedOperationException("This method will be removed with refactor.");
//    return signedPreKeyStore.containsSignedPreKey(signedPreKeyId);
  }

  @Override
  public void removeSignedPreKey(int signedPreKeyId) {
    throw new UnsupportedOperationException("This method will be removed with refactor.");
//    signedPreKeyStore.removeSignedPreKey(signedPreKeyId);
  }
}
