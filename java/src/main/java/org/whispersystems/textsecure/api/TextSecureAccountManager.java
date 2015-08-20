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


import com.google.protobuf.ByteString;

import org.whispersystems.libaxolotl.IdentityKey;
import org.whispersystems.libaxolotl.IdentityKeyPair;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.ecc.ECPublicKey;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.textsecure.api.messages.multidevice.DeviceInfo;
import org.whispersystems.textsecure.api.push.ContactTokenDetails;
import org.whispersystems.textsecure.api.push.SignedPreKeyEntity;
import org.whispersystems.textsecure.api.push.TrustStore;
import org.whispersystems.textsecure.internal.crypto.ProvisioningCipher;
import org.whispersystems.textsecure.internal.push.PushServiceSocket;
import org.whispersystems.textsecure.internal.util.Base64;
import org.whispersystems.textsecure.internal.util.StaticCredentialsProvider;
import org.whispersystems.textsecure.internal.util.Util;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.whispersystems.textsecure.internal.push.ProvisioningProtos.ProvisionMessage;

/**
 * The main interface for creating, registering, and
 * managing a TextSecure account.
 *
 * @author Moxie Marlinspike
 */
public class TextSecureAccountManager {

  private final PushServiceSocket pushServiceSocket;
  private final String            user;
  private final String            userAgent;

  /**
   * Construct a TextSecureAccountManager.
   *
   * @param url The URL for the TextSecure server.
   * @param trustStore The {@link org.whispersystems.textsecure.api.push.TrustStore} for the TextSecure server's TLS certificate.
   * @param user A TextSecure phone number.
   * @param password A TextSecure password.
   * @param userAgent A string which identifies the client software.
   */
  public TextSecureAccountManager(String url, TrustStore trustStore,
                                  String user, String password,
                                  String userAgent)
  {
    this.pushServiceSocket = new PushServiceSocket(url, trustStore, new StaticCredentialsProvider(user, password, null), userAgent);
    this.user              = user;
    this.userAgent         = userAgent;
  }

  /**
   * Register/Unregister a Google Cloud Messaging registration ID.
   *
   * @param gcmRegistrationId The GCM id to register.  A call with an absent value will unregister.
   * @throws IOException
   */
  public void setGcmId(Optional<String> gcmRegistrationId) throws IOException {
    if (gcmRegistrationId.isPresent()) {
      this.pushServiceSocket.registerGcmId(gcmRegistrationId.get());
    } else {
      this.pushServiceSocket.unregisterGcmId();
    }
  }

  /**
   * Request an SMS verification code.  On success, the server will send
   * an SMS verification code to this TextSecure user.
   *
   * @throws IOException
   */
  public void requestSmsVerificationCode() throws IOException {
    this.pushServiceSocket.createAccount(false);
  }

  /**
   * Request a Voice verification code.  On success, the server will
   * make a voice call to this TextSecure user.
   *
    * @throws IOException
   */
  public void requestVoiceVerificationCode() throws IOException {
    this.pushServiceSocket.createAccount(true);
  }

  /**
   * Verify a TextSecure account with a received SMS or voice verification code.
   *
   * @param verificationCode The verification code received via SMS or Voice
   *                         (see {@link #requestSmsVerificationCode} and
   *                         {@link #requestVoiceVerificationCode}).
   * @param signalingKey 52 random bytes.  A 32 byte AES key and a 20 byte Hmac256 key,
   *                     concatenated.
   * @param axolotlRegistrationId A random 14-bit number that identifies this TextSecure install.
   *                              This value should remain consistent across registrations for the
   *                              same install, but probabilistically differ across registrations
   *                              for separate installs.
   *
   * @throws IOException
   */
  public void verifyAccountWithCode(String verificationCode, String signalingKey, int axolotlRegistrationId)
      throws IOException
  {
    this.pushServiceSocket.verifyAccountCode(verificationCode, signalingKey,
                                             axolotlRegistrationId);
  }

  /**
   * Verify a TextSecure account with a signed token from a trusted source.
   *
   * @param verificationToken The signed token provided by a trusted server.

   * @param signalingKey 52 random bytes.  A 32 byte AES key and a 20 byte Hmac256 key,
   *                     concatenated.
   * @param axolotlRegistrationId A random 14-bit number that identifies this TextSecure install.
   *                              This value should remain consistent across registrations for the
   *                              same install, but probabilistically differ across registrations
   *                              for separate installs.
   *
   * @throws IOException
   */
  public void verifyAccountWithToken(String verificationToken, String signalingKey, int axolotlRegistrationId)
      throws IOException
  {
    this.pushServiceSocket.verifyAccountToken(verificationToken, signalingKey, axolotlRegistrationId);
  }

  /**
   * Register an identity key, last resort key, signed prekey, and list of one time prekeys
   * with the server.
   *
   * @param identityKey The client's long-term identity keypair.
   * @param lastResortKey The client's "last resort" prekey.
   * @param signedPreKey The client's signed prekey.
   * @param oneTimePreKeys The client's list of one-time prekeys.
   *
   * @throws IOException
   */
  public void setPreKeys(IdentityKey identityKey, PreKeyRecord lastResortKey,
                         SignedPreKeyRecord signedPreKey, List<PreKeyRecord> oneTimePreKeys)
      throws IOException
  {
    this.pushServiceSocket.registerPreKeys(identityKey, lastResortKey, signedPreKey, oneTimePreKeys);
  }

  /**
   * @return The server's count of currently available (eg. unused) prekeys for this user.
   * @throws IOException
   */
  public int getPreKeysCount() throws IOException {
    return this.pushServiceSocket.getAvailablePreKeys();
  }

  /**
   * Set the client's signed prekey.
   *
   * @param signedPreKey The client's new signed prekey.
   * @throws IOException
   */
  public void setSignedPreKey(SignedPreKeyRecord signedPreKey) throws IOException {
    this.pushServiceSocket.setCurrentSignedPreKey(signedPreKey);
  }

  /**
   * @return The server's view of the client's current signed prekey.
   * @throws IOException
   */
  public SignedPreKeyEntity getSignedPreKey() throws IOException {
    return this.pushServiceSocket.getCurrentSignedPreKey();
  }

  /**
   * Checks whether a contact is currently registered with the server.
   *
   * @param e164number The contact to check.
   * @return An optional ContactTokenDetails, present if registered, absent if not.
   * @throws IOException
   */
  public Optional<ContactTokenDetails> getContact(String e164number) throws IOException {
    String              contactToken        = createDirectoryServerToken(e164number, true);
    ContactTokenDetails contactTokenDetails = this.pushServiceSocket.getContactTokenDetails(contactToken);

    if (contactTokenDetails != null) {
      contactTokenDetails.setNumber(e164number);
    }

    return Optional.fromNullable(contactTokenDetails);
  }

  /**
   * Checks which contacts in a set are registered with the server.
   *
   * @param e164numbers The contacts to check.
   * @return A list of ContactTokenDetails for the registered users.
   * @throws IOException
   */
  public List<ContactTokenDetails> getContacts(Set<String> e164numbers)
      throws IOException
  {
    Map<String, String>       contactTokensMap = createDirectoryServerTokenMap(e164numbers);
    List<ContactTokenDetails> activeTokens     = this.pushServiceSocket.retrieveDirectory(contactTokensMap.keySet());

    for (ContactTokenDetails activeToken : activeTokens) {
      activeToken.setNumber(contactTokensMap.get(activeToken.getToken()));
    }

    return activeTokens;
  }

  public String getNewDeviceVerificationCode() throws IOException {
    return this.pushServiceSocket.getNewDeviceVerificationCode();
  }

  public void addDevice(String deviceIdentifier,
                        ECPublicKey deviceKey,
                        IdentityKeyPair identityKeyPair,
                        String code)
      throws InvalidKeyException, IOException
  {
    ProvisioningCipher cipher  = new ProvisioningCipher(deviceKey);
    ProvisionMessage   message = ProvisionMessage.newBuilder()
                                                 .setIdentityKeyPublic(ByteString.copyFrom(identityKeyPair.getPublicKey().serialize()))
                                                 .setIdentityKeyPrivate(ByteString.copyFrom(identityKeyPair.getPrivateKey().serialize()))
                                                 .setNumber(user)
                                                 .setProvisioningCode(code)
                                                 .build();

    byte[] ciphertext = cipher.encrypt(message);
    this.pushServiceSocket.sendProvisioningMessage(deviceIdentifier, ciphertext);
  }

  public List<DeviceInfo> getDevices() throws IOException {
    return this.pushServiceSocket.getDevices();
  }

  public void removeDevice(long deviceId) throws IOException {
    this.pushServiceSocket.removeDevice(deviceId);
  }

  private String createDirectoryServerToken(String e164number, boolean urlSafe) {
    try {
      MessageDigest digest  = MessageDigest.getInstance("SHA1");
      byte[]        token   = Util.trim(digest.digest(e164number.getBytes()), 10);
      String        encoded = Base64.encodeBytesWithoutPadding(token);

      if (urlSafe) return encoded.replace('+', '-').replace('/', '_');
      else         return encoded;
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private Map<String, String> createDirectoryServerTokenMap(Collection<String> e164numbers) {
    Map<String,String> tokenMap = new HashMap<>(e164numbers.size());

    for (String number : e164numbers) {
      tokenMap.put(createDirectoryServerToken(number, false), number);
    }

    return tokenMap;
  }

}
