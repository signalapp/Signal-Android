package org.whispersystems.textsecure.api;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.push.ContactTokenDetails;
import org.whispersystems.textsecure.push.PushServiceSocket;
import org.whispersystems.textsecure.push.SignedPreKeyEntity;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class TextSecureAccountManager {

  private final PushServiceSocket pushServiceSocket;

  public TextSecureAccountManager(String url, PushServiceSocket.TrustStore trustStore,
                                  String user, String password)
  {
    this.pushServiceSocket = new PushServiceSocket(url, trustStore, user, password);
  }

  public void setGcmId(Optional<String> gcmRegistrationId) throws IOException {
    if (gcmRegistrationId.isPresent()) {
      this.pushServiceSocket.registerGcmId(gcmRegistrationId.get());
    } else {
      this.pushServiceSocket.unregisterGcmId();
    }
  }

  public void requestSmsVerificationCode() throws IOException {
    this.pushServiceSocket.createAccount(false);
  }

  public void requestVoiceVerificationCode() throws IOException {
    this.pushServiceSocket.createAccount(true);
  }

  public void verifyAccount(String verificationCode, String signalingKey,
                            boolean supportsSms, int axolotlRegistrationId)
      throws IOException
  {
    this.pushServiceSocket.verifyAccount(verificationCode, signalingKey,
                                         supportsSms, axolotlRegistrationId);
  }

  public void setPreKeys(IdentityKey identityKey, PreKeyRecord lastResortKey,
                         SignedPreKeyRecord signedPreKey, List<PreKeyRecord> oneTimePreKeys)
      throws IOException
  {
    this.pushServiceSocket.registerPreKeys(identityKey, lastResortKey, signedPreKey, oneTimePreKeys);
  }

  public int getPreKeysCount() throws IOException {
    return this.pushServiceSocket.getAvailablePreKeys();
  }

  public void setSignedPreKey(SignedPreKeyRecord signedPreKey) throws IOException {
    this.pushServiceSocket.setCurrentSignedPreKey(signedPreKey);
  }

  public SignedPreKeyEntity getSignedPreKey() throws IOException {
    return this.pushServiceSocket.getCurrentSignedPreKey();
  }

  public Optional<ContactTokenDetails> getContact(String contactToken) throws IOException {
    return Optional.fromNullable(this.pushServiceSocket.getContactTokenDetails(contactToken));
  }

  public List<ContactTokenDetails> getContacts(Set<String> contactTokens) {
    return this.pushServiceSocket.retrieveDirectory(contactTokens);
  }

}
