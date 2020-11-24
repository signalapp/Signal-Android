/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api;


import com.google.protobuf.ByteString;

import org.whispersystems.curve25519.Curve25519;
import org.whispersystems.curve25519.Curve25519KeyPair;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.crypto.ProfileCipherOutputStream;
import org.whispersystems.signalservice.api.messages.calls.TurnServerInfo;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.contacts.crypto.ContactDiscoveryCipher;
import org.whispersystems.signalservice.internal.contacts.crypto.Quote;
import org.whispersystems.signalservice.internal.contacts.crypto.RemoteAttestation;
import org.whispersystems.signalservice.internal.contacts.crypto.RemoteAttestationKeys;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedQuoteException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;
import org.whispersystems.signalservice.internal.contacts.entities.DiscoveryRequest;
import org.whispersystems.signalservice.internal.contacts.entities.DiscoveryResponse;
import org.whispersystems.signalservice.internal.contacts.entities.RemoteAttestationRequest;
import org.whispersystems.signalservice.internal.contacts.entities.RemoteAttestationResponse;
import org.whispersystems.signalservice.internal.crypto.ProvisioningCipher;
import org.whispersystems.signalservice.internal.push.ProfileAvatarData;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.push.http.ProfileCipherOutputStreamFactory;
import org.whispersystems.signalservice.internal.util.Base64;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.IOException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.whispersystems.signalservice.internal.push.ProvisioningProtos.ProvisionMessage;

/**
 * The main interface for creating, registering, and
 * managing a Signal Service account.
 *
 * @author Moxie Marlinspike
 */
public class SignalServiceAccountManager {

  private static final String TAG = SignalServiceAccountManager.class.getSimpleName();

  private final PushServiceSocket pushServiceSocket;
  private final String            user;
  private final String            userAgent;

  /**
   * Construct a SignalServiceAccountManager.
   *
   * @param configuration The URL for the Signal Service.
   * @param user A Signal Service phone number.
   * @param password A Signal Service password.
   * @param userAgent A string which identifies the client software.
   */
  public SignalServiceAccountManager(SignalServiceConfiguration configuration,
                                     String user, String password,
                                     String userAgent)
  {
    this(configuration, new StaticCredentialsProvider(user, password, null), userAgent);
  }

  public SignalServiceAccountManager(SignalServiceConfiguration configuration,
                                     CredentialsProvider credentialsProvider,
                                     String userAgent)
  {
    this.pushServiceSocket = new PushServiceSocket(configuration, credentialsProvider, userAgent);
    this.user              = credentialsProvider.getUser();
    this.userAgent         = userAgent;
  }

  public byte[] getSenderCertificate() throws IOException {
    return this.pushServiceSocket.getSenderCertificate();
  }

  public void setPin(Optional<String> pin) throws IOException {
    if (pin.isPresent()) {
      this.pushServiceSocket.setPin(pin.get());
    } else {
      this.pushServiceSocket.removePin();
    }
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
   * an SMS verification code to this Signal user.
   *
   * @throws IOException
   */
  public void requestSmsVerificationCode(boolean androidSmsRetrieverSupported, Optional<String> captchaToken) throws IOException {
    this.pushServiceSocket.requestSmsVerificationCode(androidSmsRetrieverSupported, captchaToken);
  }

  /**
   * Request a Voice verification code.  On success, the server will
   * make a voice call to this Signal user.
   *
    * @throws IOException
   */
  public void requestVoiceVerificationCode(Locale locale, Optional<String> captchaToken) throws IOException {
    this.pushServiceSocket.requestVoiceVerificationCode(locale, captchaToken);
  }

  /**
   * Verify a Signal Service account with a received SMS or voice verification code.
   *
   * @param verificationCode The verification code received via SMS or Voice
   *                         (see {@link #requestSmsVerificationCode} and
   *                         {@link #requestVoiceVerificationCode}).
   * @param signalingKey 52 random bytes.  A 32 byte AES key and a 20 byte Hmac256 key,
   *                     concatenated.
   * @param signalProtocolRegistrationId A random 14-bit number that identifies this Signal install.
   *                                     This value should remain consistent across registrations for the
   *                                     same install, but probabilistically differ across registrations
   *                                     for separate installs.
   *
   * @throws IOException
   */
  public void verifyAccountWithCode(String verificationCode, String signalingKey, int signalProtocolRegistrationId, boolean fetchesMessages, String pin,
                                    byte[] unidentifiedAccessKey, boolean unrestrictedUnidentifiedAccess)
      throws IOException
  {
    this.pushServiceSocket.verifyAccountCode(verificationCode, signalingKey,
                                             signalProtocolRegistrationId,
                                             fetchesMessages, pin,
                                             unidentifiedAccessKey,
                                             unrestrictedUnidentifiedAccess);
  }

  /**
   * Refresh account attributes with server.
   *
   * @param signalingKey 52 random bytes.  A 32 byte AES key and a 20 byte Hmac256 key, concatenated.
   * @param signalProtocolRegistrationId A random 14-bit number that identifies this Signal install.
   *                                     This value should remain consistent across registrations for the same
   *                                     install, but probabilistically differ across registrations for
   *                                     separate installs.
   *
   * @throws IOException
   */
  public void setAccountAttributes(String signalingKey, int signalProtocolRegistrationId, boolean fetchesMessages, String pin,
                                   byte[] unidentifiedAccessKey, boolean unrestrictedUnidentifiedAccess)
      throws IOException
  {
    this.pushServiceSocket.setAccountAttributes(signalingKey, signalProtocolRegistrationId, fetchesMessages, pin,
                                                unidentifiedAccessKey, unrestrictedUnidentifiedAccess);
  }

  /**
   * Register an identity key, signed prekey, and list of one time prekeys
   * with the server.
   *
   * @param identityKey The client's long-term identity keypair.
   * @param signedPreKey The client's signed prekey.
   * @param oneTimePreKeys The client's list of one-time prekeys.
   *
   * @throws IOException
   */
  public void setPreKeys(IdentityKey identityKey, SignedPreKeyRecord signedPreKey, List<PreKeyRecord> oneTimePreKeys)
      throws IOException
  {
    this.pushServiceSocket.registerPreKeys(identityKey, signedPreKey, oneTimePreKeys);
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

  public List<String> getRegisteredUsers(KeyStore iasKeyStore, Set<String> e164numbers, String mrenclave)
      throws IOException, Quote.InvalidQuoteFormatException, UnauthenticatedQuoteException, SignatureException, UnauthenticatedResponseException
  {
    try {
      String            authorization = this.pushServiceSocket.getContactDiscoveryAuthorization();
      Curve25519        curve         = Curve25519.getInstance(Curve25519.BEST);
      Curve25519KeyPair keyPair       = curve.generateKeyPair();

      ContactDiscoveryCipher                        cipher              = new ContactDiscoveryCipher();
      RemoteAttestationRequest                      attestationRequest  = new RemoteAttestationRequest(keyPair.getPublicKey());
      Pair<RemoteAttestationResponse, List<String>> attestationResponse = this.pushServiceSocket.getContactDiscoveryRemoteAttestation(authorization, attestationRequest, mrenclave);

      RemoteAttestationKeys keys      = new RemoteAttestationKeys(keyPair, attestationResponse.first().getServerEphemeralPublic(), attestationResponse.first().getServerStaticPublic());
      Quote                 quote     = new Quote(attestationResponse.first().getQuote());
      byte[]                requestId = cipher.getRequestId(keys, attestationResponse.first());

      cipher.verifyServerQuote(quote, attestationResponse.first().getServerStaticPublic(), mrenclave);
      cipher.verifyIasSignature(iasKeyStore, attestationResponse.first().getCertificates(), attestationResponse.first().getSignatureBody(), attestationResponse.first().getSignature(), quote);

      RemoteAttestation remoteAttestation = new RemoteAttestation(requestId, keys);
      List<String>      addressBook       = new LinkedList<String>();

      for (String e164number : e164numbers) {
        addressBook.add(e164number.substring(1));
      }

      DiscoveryRequest  request  = cipher.createDiscoveryRequest(addressBook, remoteAttestation);
      DiscoveryResponse response = this.pushServiceSocket.getContactDiscoveryRegisteredUsers(authorization, request, attestationResponse.second(), mrenclave);
      byte[]            data     = cipher.getDiscoveryResponseData(response, remoteAttestation);

      Iterator<String> addressBookIterator = addressBook.iterator();
      List<String>     results             = new LinkedList<String>();

      for (byte aData : data) {
        String candidate = addressBookIterator.next();

        if (aData != 0) results.add('+' + candidate);
      }

      return results;
    } catch (InvalidCiphertextException e) {
      throw new UnauthenticatedResponseException(e);
    }
  }

  public void reportContactDiscoveryServiceMatch() {
    try {
      this.pushServiceSocket.reportContactDiscoveryServiceMatch();
    } catch (IOException e) {
      Log.w(TAG, "Request to indicate a contact discovery result match failed. Ignoring.", e);
    }
  }

  public void reportContactDiscoveryServiceMismatch() {
    try {
      this.pushServiceSocket.reportContactDiscoveryServiceMismatch();
    } catch (IOException e) {
      Log.w(TAG, "Request to indicate a contact discovery result mismatch failed. Ignoring.", e);
    }
  }

  public void reportContactDiscoveryServiceAttestationError(String reason) {
    try {
      this.pushServiceSocket.reportContactDiscoveryServiceAttestationError(reason);
    } catch (IOException e) {
      Log.w(TAG, "Request to indicate a contact discovery attestation error failed. Ignoring.", e);
    }
  }

  public void reportContactDiscoveryServiceUnexpectedError(String reason) {
    try {
      this.pushServiceSocket.reportContactDiscoveryServiceUnexpectedError(reason);
    } catch (IOException e) {
      Log.w(TAG, "Request to indicate a contact discovery unexpected error failed. Ignoring.", e);
    }
  }

  public String getNewDeviceVerificationCode() throws IOException {
    return this.pushServiceSocket.getNewDeviceVerificationCode();
  }

  public void addDevice(String deviceIdentifier,
                        ECPublicKey deviceKey,
                        IdentityKeyPair identityKeyPair,
                        Optional<byte[]> profileKey,
                        String code)
      throws InvalidKeyException, IOException
  {
    ProvisioningCipher       cipher  = new ProvisioningCipher(deviceKey);
    ProvisionMessage.Builder message = ProvisionMessage.newBuilder()
                                                       .setIdentityKeyPublic(ByteString.copyFrom(identityKeyPair.getPublicKey().serialize()))
                                                       .setIdentityKeyPrivate(ByteString.copyFrom(identityKeyPair.getPrivateKey().serialize()))
                                                       .setNumber(user)
                                                       .setProvisioningCode(code);

    if (profileKey.isPresent()) {
      message.setProfileKey(ByteString.copyFrom(profileKey.get()));
    }

    byte[] ciphertext = cipher.encrypt(message.build());
    this.pushServiceSocket.sendProvisioningMessage(deviceIdentifier, ciphertext);
  }

  public List<DeviceInfo> getDevices() throws IOException {
    return this.pushServiceSocket.getDevices();
  }

  public void removeDevice(long deviceId) throws IOException {
    this.pushServiceSocket.removeDevice(deviceId);
  }

  public TurnServerInfo getTurnServerInfo() throws IOException {
    return this.pushServiceSocket.getTurnServerInfo();
  }

  public void setProfileName(byte[] key, String name)
      throws IOException
  {
    if (name == null) name = "";

    String ciphertextName = Base64.encodeBytesWithoutPadding(new ProfileCipher(key).encryptName(name.getBytes("UTF-8"), ProfileCipher.NAME_PADDED_LENGTH));

    this.pushServiceSocket.setProfileName(ciphertextName);
  }

  public void setProfileAvatar(byte[] key, StreamDetails avatar)
      throws IOException
  {
    ProfileAvatarData profileAvatarData = null;

    if (avatar != null) {
      profileAvatarData = new ProfileAvatarData(avatar.getStream(),
                                                ProfileCipherOutputStream.getCiphertextLength(avatar.getLength()),
                                                avatar.getContentType(),
                                                new ProfileCipherOutputStreamFactory(key));
    }

    this.pushServiceSocket.setProfileAvatar(profileAvatarData);
  }

  public void setSoTimeoutMillis(long soTimeoutMillis) {
    this.pushServiceSocket.setSoTimeoutMillis(soTimeoutMillis);
  }

  public void cancelInFlightRequests() {
    this.pushServiceSocket.cancelInFlightRequests();
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
    Map<String, String> tokenMap = new HashMap<String, String>(e164numbers.size());

    for (String number : e164numbers) {
      tokenMap.put(createDirectoryServerToken(number, false), number);
    }

    return tokenMap;
  }

}
