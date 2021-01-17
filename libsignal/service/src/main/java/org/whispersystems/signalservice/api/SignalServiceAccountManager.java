/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api;


import com.google.protobuf.ByteString;

import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.crypto.ProfileCipherOutputStream;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.messages.calls.TurnServerInfo;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfileWrite;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;
import org.whispersystems.signalservice.api.push.exceptions.NoContentException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.storage.SignalStorageCipher;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageModels;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.storage.StorageKey;
import org.whispersystems.signalservice.api.storage.StorageManifestKey;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.StreamDetails;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.contacts.crypto.ContactDiscoveryCipher;
import org.whispersystems.signalservice.internal.contacts.crypto.Quote;
import org.whispersystems.signalservice.internal.contacts.crypto.RemoteAttestation;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedQuoteException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;
import org.whispersystems.signalservice.internal.contacts.entities.DiscoveryRequest;
import org.whispersystems.signalservice.internal.contacts.entities.DiscoveryResponse;
import org.whispersystems.signalservice.internal.crypto.ProvisioningCipher;
import org.whispersystems.signalservice.api.account.AccountAttributes;
import org.whispersystems.signalservice.internal.push.ProfileAvatarData;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.push.RemoteAttestationUtil;
import org.whispersystems.signalservice.internal.push.RemoteConfigResponse;
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse;
import org.whispersystems.signalservice.internal.push.http.ProfileCipherOutputStreamFactory;
import org.whispersystems.signalservice.internal.storage.protos.ManifestRecord;
import org.whispersystems.signalservice.internal.storage.protos.ReadOperation;
import org.whispersystems.signalservice.internal.storage.protos.StorageItem;
import org.whispersystems.signalservice.internal.storage.protos.StorageItems;
import org.whispersystems.signalservice.internal.storage.protos.StorageManifest;
import org.whispersystems.signalservice.internal.storage.protos.WriteOperation;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.whispersystems.signalservice.internal.push.ProvisioningProtos.ProvisionMessage;
import static org.whispersystems.signalservice.internal.push.ProvisioningProtos.ProvisioningVersion;

/**
 * The main interface for creating, registering, and
 * managing a Signal Service account.
 *
 * @author Moxie Marlinspike
 */
public class SignalServiceAccountManager {

  private static final String TAG = SignalServiceAccountManager.class.getSimpleName();

  private final PushServiceSocket   pushServiceSocket;
  private final CredentialsProvider credentials;
  private final String              userAgent;
  private final GroupsV2Operations  groupsV2Operations;

  /**
   * Construct a SignalServiceAccountManager.
   *
   * @param configuration The URL for the Signal Service.
   * @param uuid The Signal Service UUID.
   * @param e164 The Signal Service phone number.
   * @param password A Signal Service password.
   * @param signalAgent A string which identifies the client software.
   */
  public SignalServiceAccountManager(SignalServiceConfiguration configuration,
                                     UUID uuid, String e164, String password,
                                     String signalAgent, boolean automaticNetworkRetry)
  {
    this(configuration,
         new StaticCredentialsProvider(uuid, e164, password, null),
         signalAgent,
         new GroupsV2Operations(ClientZkOperations.create(configuration)),
         automaticNetworkRetry);
  }

  public SignalServiceAccountManager(SignalServiceConfiguration configuration,
                                     CredentialsProvider credentialsProvider,
                                     String signalAgent,
                                     GroupsV2Operations groupsV2Operations,
                                     boolean automaticNetworkRetry)
  {
    this.groupsV2Operations = groupsV2Operations;
    this.pushServiceSocket  = new PushServiceSocket(configuration, credentialsProvider, signalAgent, groupsV2Operations.getProfileOperations(), automaticNetworkRetry);
    this.credentials        = credentialsProvider;
    this.userAgent          = signalAgent;
  }

  public byte[] getSenderCertificate() throws IOException {
    return this.pushServiceSocket.getSenderCertificate();
  }

  public byte[] getSenderCertificateForPhoneNumberPrivacy() throws IOException {
    return this.pushServiceSocket.getUuidOnlySenderCertificate();
  }

  /**
   * V1 PINs are no longer used in favor of V2 PINs stored on KBS.
   *
   * You can remove a V1 PIN, but typically this is unnecessary, as setting a V2 PIN via
   * {@link KeyBackupService.Session#enableRegistrationLock(MasterKey)}} will automatically clear the
   * V1 PIN on the service.
   */
  public void removeRegistrationLockV1() throws IOException {
    this.pushServiceSocket.removeRegistrationLockV1();
  }

  public UUID getOwnUuid() throws IOException {
    return this.pushServiceSocket.getOwnUuid();
  }

  public KeyBackupService getKeyBackupService(KeyStore iasKeyStore,
                                              String enclaveName,
                                              byte[] serviceId,
                                              String mrenclave,
                                              int tries)
  {
    return new KeyBackupService(iasKeyStore, enclaveName, serviceId, mrenclave, pushServiceSocket, tries);
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
   * Request a push challenge. A number will be pushed to the GCM (FCM) id. This can then be used
   * during SMS/call requests to bypass the CAPTCHA.
   *
   * @param gcmRegistrationId The GCM (FCM) id to use.
   * @param e164number        The number to associate it with.
   * @throws IOException
   */
  public void requestPushChallenge(String gcmRegistrationId, String e164number) throws IOException {
    this.pushServiceSocket.requestPushChallenge(gcmRegistrationId, e164number);
  }

  /**
   * Request an SMS verification code.  On success, the server will send
   * an SMS verification code to this Signal user.
   *
   * @param androidSmsRetrieverSupported
   * @param captchaToken                 If the user has done a CAPTCHA, include this.
   * @param challenge                    If present, it can bypass the CAPTCHA.
   * @throws IOException
   */
  public void requestSmsVerificationCode(boolean androidSmsRetrieverSupported, Optional<String> captchaToken, Optional<String> challenge) throws IOException {
    this.pushServiceSocket.requestSmsVerificationCode(androidSmsRetrieverSupported, captchaToken, challenge);
  }

  /**
   * Request a Voice verification code.  On success, the server will
   * make a voice call to this Signal user.
   *
   * @param locale
   * @param captchaToken If the user has done a CAPTCHA, include this.
   * @param challenge    If present, it can bypass the CAPTCHA.
   * @throws IOException
   */
  public void requestVoiceVerificationCode(Locale locale, Optional<String> captchaToken, Optional<String> challenge) throws IOException {
    this.pushServiceSocket.requestVoiceVerificationCode(locale, captchaToken, challenge);
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
   * @param pin Deprecated, only supply the pin if you did not find a registrationLock on KBS.
   * @param registrationLock Only supply if found on KBS.
   * @return The UUID of the user that was registered.
   * @throws IOException
   */
  public VerifyAccountResponse verifyAccountWithCode(String verificationCode, String signalingKey, int signalProtocolRegistrationId, boolean fetchesMessages,
                                                     String pin, String registrationLock,
                                                     byte[] unidentifiedAccessKey, boolean unrestrictedUnidentifiedAccess,
                                                     AccountAttributes.Capabilities capabilities,
                                                     boolean discoverableByPhoneNumber)
      throws IOException
  {
    return this.pushServiceSocket.verifyAccountCode(verificationCode, signalingKey,
                                                    signalProtocolRegistrationId,
                                                    fetchesMessages,
                                                    pin, registrationLock,
                                                    unidentifiedAccessKey,
                                                    unrestrictedUnidentifiedAccess,
                                                    capabilities,
                                                    discoverableByPhoneNumber);
  }

  /**
   * Refresh account attributes with server.
   *
   * @param signalingKey 52 random bytes.  A 32 byte AES key and a 20 byte Hmac256 key, concatenated.
   * @param signalProtocolRegistrationId A random 14-bit number that identifies this Signal install.
   *                                     This value should remain consistent across registrations for the same
   *                                     install, but probabilistically differ across registrations for
   *                                     separate installs.
   * @param pin Only supply if pin has not yet been migrated to KBS.
   * @param registrationLock Only supply if found on KBS.
   *
   * @throws IOException
   */
  public void setAccountAttributes(String signalingKey, int signalProtocolRegistrationId, boolean fetchesMessages,
                                   String pin, String registrationLock,
                                   byte[] unidentifiedAccessKey, boolean unrestrictedUnidentifiedAccess,
                                   AccountAttributes.Capabilities capabilities,
                                   boolean discoverableByPhoneNumber)
      throws IOException
  {
    this.pushServiceSocket.setAccountAttributes(signalingKey, signalProtocolRegistrationId, fetchesMessages,
                                                pin, registrationLock,
                                                unidentifiedAccessKey, unrestrictedUnidentifiedAccess,
                                                capabilities,
                                                discoverableByPhoneNumber);
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

  public Map<String, UUID> getRegisteredUsers(KeyStore iasKeyStore, Set<String> e164numbers, String mrenclave)
      throws IOException, Quote.InvalidQuoteFormatException, UnauthenticatedQuoteException, SignatureException, UnauthenticatedResponseException, InvalidKeyException
  {
    if (e164numbers.isEmpty()) {
      return Collections.emptyMap();
    }

    try {
      String                         authorization = this.pushServiceSocket.getContactDiscoveryAuthorization();
      Map<String, RemoteAttestation> attestations  = RemoteAttestationUtil.getAndVerifyMultiRemoteAttestation(pushServiceSocket,
                                                                                                              PushServiceSocket.ClientSet.ContactDiscovery,
                                                                                                              iasKeyStore,
                                                                                                              mrenclave,
                                                                                                              mrenclave,
                                                                                                              authorization);

      List<String> addressBook = new ArrayList<>(e164numbers.size());

      for (String e164number : e164numbers) {
        addressBook.add(e164number.substring(1));
      }

      List<String>      cookies  = attestations.values().iterator().next().getCookies();
      DiscoveryRequest  request  = ContactDiscoveryCipher.createDiscoveryRequest(addressBook, attestations);
      DiscoveryResponse response = this.pushServiceSocket.getContactDiscoveryRegisteredUsers(authorization, request, cookies, mrenclave);
      byte[]            data     = ContactDiscoveryCipher.getDiscoveryResponseData(response, attestations.values());

      HashMap<String, UUID> results         = new HashMap<>(addressBook.size());
      DataInputStream       uuidInputStream = new DataInputStream(new ByteArrayInputStream(data));

      for (String candidate : addressBook) {
        long candidateUuidHigh = uuidInputStream.readLong();
        long candidateUuidLow  = uuidInputStream.readLong();
        if (candidateUuidHigh != 0 || candidateUuidLow != 0) {
          results.put('+' + candidate, new UUID(candidateUuidHigh, candidateUuidLow));
        }
      }

      return results;
    } catch (InvalidCiphertextException e) {
      throw new UnauthenticatedResponseException(e);
    }
  }

  public Optional<SignalStorageManifest> getStorageManifest(StorageKey storageKey) throws IOException {
    try {
      String          authToken       = this.pushServiceSocket.getStorageAuth();
      StorageManifest storageManifest = this.pushServiceSocket.getStorageManifest(authToken);

      return Optional.of(SignalStorageModels.remoteToLocalStorageManifest(storageManifest, storageKey));
    } catch (InvalidKeyException | NotFoundException e) {
      Log.w(TAG, "Error while fetching manifest.", e);
      return Optional.absent();
    }
  }

  public long getStorageManifestVersion() throws IOException {
    try {
      String          authToken       = this.pushServiceSocket.getStorageAuth();
      StorageManifest storageManifest = this.pushServiceSocket.getStorageManifest(authToken);

      return  storageManifest.getVersion();
    } catch (NotFoundException e) {
      return 0;
    }
  }

  public Optional<SignalStorageManifest> getStorageManifestIfDifferentVersion(StorageKey storageKey, long manifestVersion) throws IOException, InvalidKeyException {
    try {
      String          authToken       = this.pushServiceSocket.getStorageAuth();
      StorageManifest storageManifest = this.pushServiceSocket.getStorageManifestIfDifferentVersion(authToken, manifestVersion);

      if (storageManifest.getValue().isEmpty()) {
        Log.w(TAG, "Got an empty storage manifest!");
        return Optional.absent();
      }

      return Optional.of(SignalStorageModels.remoteToLocalStorageManifest(storageManifest, storageKey));
    } catch (NoContentException e) {
      return Optional.absent();
    }
  }

  public List<SignalStorageRecord> readStorageRecords(StorageKey storageKey, List<StorageId> storageKeys) throws IOException, InvalidKeyException {
    if (storageKeys.isEmpty()) {
      return Collections.emptyList();
    }

    List<SignalStorageRecord> result    = new ArrayList<>();
    ReadOperation.Builder     operation = ReadOperation.newBuilder();
    Map<ByteString, Integer>  typeMap   = new HashMap<>();

    for (StorageId key : storageKeys) {
      typeMap.put(ByteString.copyFrom(key.getRaw()), key.getType());

      if (StorageId.isKnownType(key.getType())) {
        operation.addReadKey(ByteString.copyFrom(key.getRaw()));
      } else {
        result.add(SignalStorageRecord.forUnknown(key));
      }
    }

    String       authToken = this.pushServiceSocket.getStorageAuth();
    StorageItems items     = this.pushServiceSocket.readStorageItems(authToken, operation.build());

    for (StorageItem item : items.getItemsList()) {
      Integer type = typeMap.get(item.getKey());
      if (type != null) {
        result.add(SignalStorageModels.remoteToLocalStorageRecord(item, type, storageKey));
      } else {
        Log.w(TAG, "No type found! Skipping.");
      }
    }

    return result;
  }
  /**
   * @return If there was a conflict, the latest {@link SignalStorageManifest}. Otherwise absent.
   */
  public Optional<SignalStorageManifest> resetStorageRecords(StorageKey storageKey,
                                                             SignalStorageManifest manifest,
                                                             List<SignalStorageRecord> allRecords)
      throws IOException, InvalidKeyException
  {
    return writeStorageRecords(storageKey, manifest, allRecords, Collections.<byte[]>emptyList(), true);
  }

  /**
   * @return If there was a conflict, the latest {@link SignalStorageManifest}. Otherwise absent.
   */
  public Optional<SignalStorageManifest> writeStorageRecords(StorageKey storageKey,
                                                             SignalStorageManifest manifest,
                                                             List<SignalStorageRecord> inserts,
                                                             List<byte[]> deletes)
      throws IOException, InvalidKeyException
  {
    return writeStorageRecords(storageKey, manifest, inserts, deletes, false);
  }

  /**
   * @return If there was a conflict, the latest {@link SignalStorageManifest}. Otherwise absent.
   */
  private Optional<SignalStorageManifest> writeStorageRecords(StorageKey storageKey,
                                                              SignalStorageManifest manifest,
                                                              List<SignalStorageRecord> inserts,
                                                              List<byte[]> deletes,
                                                              boolean clearAll)
      throws IOException, InvalidKeyException
  {
    ManifestRecord.Builder manifestRecordBuilder = ManifestRecord.newBuilder().setVersion(manifest.getVersion());

    for (StorageId id : manifest.getStorageIds()) {
      ManifestRecord.Identifier idProto = ManifestRecord.Identifier.newBuilder()
                                                        .setRaw(ByteString.copyFrom(id.getRaw()))
                                                        .setType(ManifestRecord.Identifier.Type.forNumber(id.getType())).build();
      manifestRecordBuilder.addIdentifiers(idProto);
    }

    String             authToken       = this.pushServiceSocket.getStorageAuth();
    StorageManifestKey manifestKey     = storageKey.deriveManifestKey(manifest.getVersion());
    byte[]             encryptedRecord = SignalStorageCipher.encrypt(manifestKey, manifestRecordBuilder.build().toByteArray());
    StorageManifest    storageManifest = StorageManifest.newBuilder()
                                                         .setVersion(manifest.getVersion())
                                                         .setValue(ByteString.copyFrom(encryptedRecord))
                                                         .build();
    WriteOperation.Builder writeBuilder = WriteOperation.newBuilder().setManifest(storageManifest);

    for (SignalStorageRecord insert : inserts) {
      writeBuilder.addInsertItem(SignalStorageModels.localToRemoteStorageRecord(insert, storageKey));
    }

    if (clearAll) {
      writeBuilder.setClearAll(true);
    } else {
      for (byte[] delete : deletes) {
        writeBuilder.addDeleteKey(ByteString.copyFrom(delete));
      }
    }

    Optional<StorageManifest> conflict = this.pushServiceSocket.writeStorageContacts(authToken, writeBuilder.build());

    if (conflict.isPresent()) {
      StorageManifestKey conflictKey       = storageKey.deriveManifestKey(conflict.get().getVersion());
      byte[]             rawManifestRecord = SignalStorageCipher.decrypt(conflictKey, conflict.get().getValue().toByteArray());
      ManifestRecord     record            = ManifestRecord.parseFrom(rawManifestRecord);
      List<StorageId>    ids               = new ArrayList<>(record.getIdentifiersCount());

      for (ManifestRecord.Identifier id : record.getIdentifiersList()) {
        ids.add(StorageId.forType(id.getRaw().toByteArray(), id.getType().getNumber()));
      }

      SignalStorageManifest conflictManifest = new SignalStorageManifest(record.getVersion(), ids);

      return Optional.of(conflictManifest);
    } else {
      return Optional.absent();
    }
  }

  public Map<String, Object> getRemoteConfig() throws IOException {
    RemoteConfigResponse response = this.pushServiceSocket.getRemoteConfig();
    Map<String, Object>  out      = new HashMap<>();

    for (RemoteConfigResponse.Config config : response.getConfig()) {
      out.put(config.getName(), config.getValue() != null ? config.getValue() : config.isEnabled());
    }

    return out;
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
                                                       .setProvisioningCode(code)
                                                       .setProvisioningVersion(ProvisioningVersion.CURRENT_VALUE);

    String e164 = credentials.getE164();
    UUID   uuid = credentials.getUuid();

    if (e164 != null) {
      message.setNumber(e164);
    } else {
      throw new AssertionError("Missing phone number!");
    }

    if (uuid != null) {
      message.setUuid(uuid.toString());
    } else {
      Log.w(TAG, "[addDevice] Missing UUID.");
    }

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

  /**
   * @return The avatar URL path, if one was written.
   */
  public Optional<String> setVersionedProfile(UUID uuid, ProfileKey profileKey, String name, StreamDetails avatar)
      throws IOException
  {
    if (name == null) name = "";

    byte[]            ciphertextName    = new ProfileCipher(profileKey).encryptName(name.getBytes(StandardCharsets.UTF_8), ProfileCipher.NAME_PADDED_LENGTH);
    boolean           hasAvatar         = avatar != null;
    ProfileAvatarData profileAvatarData = null;

    if (hasAvatar) {
      profileAvatarData = new ProfileAvatarData(avatar.getStream(),
                                                ProfileCipherOutputStream.getCiphertextLength(avatar.getLength()),
                                                avatar.getContentType(),
                                                new ProfileCipherOutputStreamFactory(profileKey));
    }

    return this.pushServiceSocket.writeProfile(new SignalServiceProfileWrite(profileKey.getProfileKeyVersion(uuid).serialize(),
                                                                             ciphertextName,
                                                                             hasAvatar,
                                                                             profileKey.getCommitment(uuid).serialize()),
                                                                             profileAvatarData);
  }

  public Optional<ProfileKeyCredential> resolveProfileKeyCredential(UUID uuid, ProfileKey profileKey)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    try {
      ProfileAndCredential credential = this.pushServiceSocket.retrieveVersionedProfileAndCredential(uuid, profileKey, Optional.absent()).get(10, TimeUnit.SECONDS);
      return credential.getProfileKeyCredential();
    } catch (InterruptedException | TimeoutException e) {
      throw new PushNetworkException(e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof NonSuccessfulResponseCodeException) {
        throw (NonSuccessfulResponseCodeException) e.getCause();
      } else if (e.getCause() instanceof PushNetworkException) {
        throw (PushNetworkException) e.getCause();
      } else {
        throw new PushNetworkException(e);
      }
    }
  }

  public void setUsername(String username) throws IOException {
    this.pushServiceSocket.setUsername(username);
  }

  public void deleteUsername() throws IOException {
    this.pushServiceSocket.deleteUsername();
  }

  public void deleteAccount() throws IOException {
    this.pushServiceSocket.deleteAccount();
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
    Map<String,String> tokenMap = new HashMap<>(e164numbers.size());

    for (String number : e164numbers) {
      tokenMap.put(createDirectoryServerToken(number, false), number);
    }

    return tokenMap;
  }

  public GroupsV2Api getGroupsV2Api() {
    return new GroupsV2Api(pushServiceSocket, groupsV2Operations);
  }
}
