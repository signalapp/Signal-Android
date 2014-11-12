/**
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
