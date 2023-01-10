/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api;


import com.google.protobuf.ByteString;

import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredential;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.account.AccountAttributes;
import org.whispersystems.signalservice.api.account.ChangePhoneNumberRequest;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.api.crypto.ProfileCipher;
import org.whispersystems.signalservice.api.crypto.ProfileCipherOutputStream;
import org.whispersystems.signalservice.api.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Api;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.messages.calls.TurnServerInfo;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;
import org.whispersystems.signalservice.api.messages.multidevice.VerifyDeviceResponse;
import org.whispersystems.signalservice.api.payments.CurrencyConversions;
import org.whispersystems.signalservice.api.profiles.AvatarUploadParams;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfileWrite;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.PNI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;
import org.whispersystems.signalservice.api.push.exceptions.NoContentException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.services.CdsiV2Service;
import org.whispersystems.signalservice.api.storage.SignalStorageCipher;
import org.whispersystems.signalservice.api.storage.SignalStorageManifest;
import org.whispersystems.signalservice.api.storage.SignalStorageModels;
import org.whispersystems.signalservice.api.storage.SignalStorageRecord;
import org.whispersystems.signalservice.api.storage.StorageId;
import org.whispersystems.signalservice.api.storage.StorageKey;
import org.whispersystems.signalservice.api.storage.StorageManifestKey;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.Preconditions;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.contacts.crypto.ContactDiscoveryCipher;
import org.whispersystems.signalservice.internal.contacts.crypto.Quote;
import org.whispersystems.signalservice.internal.contacts.crypto.RemoteAttestation;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedQuoteException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;
import org.whispersystems.signalservice.internal.contacts.entities.DiscoveryRequest;
import org.whispersystems.signalservice.internal.contacts.entities.DiscoveryResponse;
import org.whispersystems.signalservice.internal.crypto.PrimaryProvisioningCipher;
import org.whispersystems.signalservice.internal.push.AuthCredentials;
import org.whispersystems.signalservice.internal.push.CdsiAuthResponse;
import org.whispersystems.signalservice.internal.push.ProfileAvatarData;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.push.RemoteAttestationUtil;
import org.whispersystems.signalservice.internal.push.RemoteConfigResponse;
import org.whispersystems.signalservice.internal.push.RequestVerificationCodeResponse;
import org.whispersystems.signalservice.internal.push.ReserveUsernameResponse;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.VerifyAccountResponse;
import org.whispersystems.signalservice.internal.push.WhoAmIResponse;
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
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import io.reactivex.rxjava3.core.Single;

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

  private static final int STORAGE_READ_MAX_ITEMS = 1000;

  private final PushServiceSocket          pushServiceSocket;
  private final CredentialsProvider        credentials;
  private final String                     userAgent;
  private final GroupsV2Operations         groupsV2Operations;
  private final SignalServiceConfiguration configuration;


  /**
   * Construct a SignalServiceAccountManager.
   * @param configuration The URL for the Signal Service.
   * @param aci The Signal Service ACI.
   * @param pni The Signal Service PNI.
   * @param e164 The Signal Service phone number.
   * @param password A Signal Service password.
   * @param signalAgent A string which identifies the client software.
   */
  public SignalServiceAccountManager(SignalServiceConfiguration configuration,
                                     ACI aci,
                                     PNI pni,
                                     String e164,
                                     int deviceId,
                                     String password,
                                     String signalAgent,
                                     boolean automaticNetworkRetry,
                                     int maxGroupSize)
  {
    this(configuration,
         new StaticCredentialsProvider(aci, pni, e164, deviceId, password),
         signalAgent,
         new GroupsV2Operations(ClientZkOperations.create(configuration), maxGroupSize),
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
    this.configuration      = configuration;
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
   * {@link KeyBackupService.PinChangeSession#enableRegistrationLock(MasterKey)}} will automatically clear the
   * V1 PIN on the service.
   */
  public void removeRegistrationLockV1() throws IOException {
    this.pushServiceSocket.removeRegistrationLockV1();
  }

  public WhoAmIResponse getWhoAmI() throws IOException {
    return this.pushServiceSocket.getWhoAmI();
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
  public void requestRegistrationPushChallenge(String gcmRegistrationId, String e164number) throws IOException {
    this.pushServiceSocket.requestPushChallenge(gcmRegistrationId, e164number);
  }

  /**
   * Request an SMS verification code.  On success, the server will send
   * an SMS verification code to this Signal user.
   *
   * @param androidSmsRetrieverSupported
   * @param captchaToken                 If the user has done a CAPTCHA, include this.
   * @param challenge                    If present, it can bypass the CAPTCHA.
   */
  public ServiceResponse<RequestVerificationCodeResponse> requestSmsVerificationCode(Locale locale, boolean androidSmsRetrieverSupported, Optional<String> captchaToken, Optional<String> challenge, Optional<String> fcmToken) {
    try {
      this.pushServiceSocket.requestSmsVerificationCode(locale, androidSmsRetrieverSupported, captchaToken, challenge);
      return ServiceResponse.forResult(new RequestVerificationCodeResponse(fcmToken), 200, null);
    } catch (IOException e) {
      return ServiceResponse.forUnknownError(e);
    }
  }

  /**
   * Request a Voice verification code.  On success, the server will
   * make a voice call to this Signal user.
   *
   * @param locale
   * @param captchaToken If the user has done a CAPTCHA, include this.
   * @param challenge    If present, it can bypass the CAPTCHA.
   */
  public ServiceResponse<RequestVerificationCodeResponse> requestVoiceVerificationCode(Locale locale, Optional<String> captchaToken, Optional<String> challenge, Optional<String> fcmToken) {
    try {
      this.pushServiceSocket.requestVoiceVerificationCode(locale, captchaToken, challenge);
      return ServiceResponse.forResult(new RequestVerificationCodeResponse(fcmToken), 200, null);
    } catch (IOException e) {
      return ServiceResponse.forUnknownError(e);
    }
  }

  /**
   * Verify a Signal Service account with a received SMS or voice verification code.
   *
   * @param verificationCode The verification code received via SMS or Voice
   *                         (see {@link #requestSmsVerificationCode} and
   *                         {@link #requestVoiceVerificationCode}).
   * @param signalProtocolRegistrationId A random 14-bit number that identifies this Signal install.
   *                                     This value should remain consistent across registrations for the
   *                                     same install, but probabilistically differ across registrations
   *                                     for separate installs.
   * @return The UUID of the user that was registered.
   * @throws IOException for various HTTP and networking errors
   */
  public ServiceResponse<VerifyAccountResponse> verifyAccount(String verificationCode,
                                                              int signalProtocolRegistrationId,
                                                              boolean fetchesMessages,
                                                              byte[] unidentifiedAccessKey,
                                                              boolean unrestrictedUnidentifiedAccess,
                                                              AccountAttributes.Capabilities capabilities,
                                                              boolean discoverableByPhoneNumber,
                                                              int pniRegistrationId)
  {
    try {
      VerifyAccountResponse response = this.pushServiceSocket.verifyAccountCode(verificationCode,
                                                                                null,
                                                                                signalProtocolRegistrationId,
                                                                                fetchesMessages,
                                                                                null,
                                                                                null,
                                                                                unidentifiedAccessKey,
                                                                                unrestrictedUnidentifiedAccess,
                                                                                capabilities,
                                                                                discoverableByPhoneNumber,
                                                                                pniRegistrationId);
      return ServiceResponse.forResult(response, 200, null);
    } catch (IOException e) {
      return ServiceResponse.forUnknownError(e);
    }
  }

  /**
   * Verify a Signal Service account with a received SMS or voice verification code with
   * registration lock.
   *
   * @param verificationCode The verification code received via SMS or Voice
   *                         (see {@link #requestSmsVerificationCode} and
   *                         {@link #requestVoiceVerificationCode}).
   * @param signalProtocolRegistrationId A random 14-bit number that identifies this Signal install.
   *                                     This value should remain consistent across registrations for the
   *                                     same install, but probabilistically differ across registrations
   *                                     for separate installs.
   * @param registrationLock Only supply if found on KBS.
   * @return The UUID of the user that was registered.
   */
  public ServiceResponse<VerifyAccountResponse> verifyAccountWithRegistrationLockPin(String verificationCode,
                                                                                     int signalProtocolRegistrationId,
                                                                                     boolean fetchesMessages,
                                                                                     String registrationLock,
                                                                                     byte[] unidentifiedAccessKey,
                                                                                     boolean unrestrictedUnidentifiedAccess,
                                                                                     AccountAttributes.Capabilities capabilities,
                                                                                     boolean discoverableByPhoneNumber,
                                                                                     int pniRegistrationId)
  {
    try {
      VerifyAccountResponse response = this.pushServiceSocket.verifyAccountCode(verificationCode,
                                                                                null,
                                                                                signalProtocolRegistrationId,
                                                                                fetchesMessages,
                                                                                null,
                                                                                registrationLock,
                                                                                unidentifiedAccessKey,
                                                                                unrestrictedUnidentifiedAccess,
                                                                                capabilities,
                                                                                discoverableByPhoneNumber,
                                                                                pniRegistrationId);
      return ServiceResponse.forResult(response, 200, null);
    } catch (IOException e) {
      return ServiceResponse.forUnknownError(e);
    }
  }

  public VerifyDeviceResponse verifySecondaryDevice(String verificationCode,
                                                    int signalProtocolRegistrationId,
                                                    boolean fetchesMessages,
                                                    byte[] unidentifiedAccessKey,
                                                    boolean unrestrictedUnidentifiedAccess,
                                                    AccountAttributes.Capabilities capabilities,
                                                    boolean discoverableByPhoneNumber,
                                                    byte[] encryptedDeviceName,
                                                    int pniRegistrationId)
      throws IOException
  {
    AccountAttributes accountAttributes = new AccountAttributes(
        null,
        signalProtocolRegistrationId,
        fetchesMessages,
        null,
        null,
        unidentifiedAccessKey,
        unrestrictedUnidentifiedAccess,
        capabilities,
        discoverableByPhoneNumber,
        Base64.encodeBytes(encryptedDeviceName),
        pniRegistrationId
    );

    return this.pushServiceSocket.verifySecondaryDevice(verificationCode, accountAttributes);
  }

  public @Nonnull ServiceResponse<VerifyAccountResponse> changeNumber(@Nonnull ChangePhoneNumberRequest changePhoneNumberRequest) {
    try {
      VerifyAccountResponse response = this.pushServiceSocket.changeNumber(changePhoneNumberRequest);
      return ServiceResponse.forResult(response, 200, null);
    } catch (IOException e) {
      return ServiceResponse.forUnknownError(e);
    }
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
  public void setAccountAttributes(String signalingKey,
                                   int signalProtocolRegistrationId,
                                   boolean fetchesMessages,
                                   String pin,
                                   String registrationLock,
                                   byte[] unidentifiedAccessKey,
                                   boolean unrestrictedUnidentifiedAccess,
                                   AccountAttributes.Capabilities capabilities,
                                   boolean discoverableByPhoneNumber,
                                   byte[] encryptedDeviceName,
                                   int pniRegistrationId)
      throws IOException
  {
    this.pushServiceSocket.setAccountAttributes(
        signalingKey,
        signalProtocolRegistrationId,
        fetchesMessages,
        pin,
        registrationLock,
        unidentifiedAccessKey,
        unrestrictedUnidentifiedAccess,
        capabilities,
        discoverableByPhoneNumber,
        encryptedDeviceName,
        pniRegistrationId
    );
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
  public void setPreKeys(ServiceIdType serviceIdType, IdentityKey identityKey, SignedPreKeyRecord signedPreKey, List<PreKeyRecord> oneTimePreKeys)
      throws IOException
  {
    this.pushServiceSocket.registerPreKeys(serviceIdType, identityKey, signedPreKey, oneTimePreKeys);
  }

  /**
   * @return The server's count of currently available (eg. unused) prekeys for this user.
   * @throws IOException
   */
  public int getPreKeysCount(ServiceIdType serviceIdType) throws IOException {
    return this.pushServiceSocket.getAvailablePreKeys(serviceIdType);
  }

  /**
   * Set the client's signed prekey.
   *
   * @param signedPreKey The client's new signed prekey.
   * @throws IOException
   */
  public void setSignedPreKey(ServiceIdType serviceIdType, SignedPreKeyRecord signedPreKey) throws IOException {
    this.pushServiceSocket.setCurrentSignedPreKey(serviceIdType, signedPreKey);
  }

  /**
   * @return The server's view of the client's current signed prekey.
   * @throws IOException
   */
  public SignedPreKeyEntity getSignedPreKey(ServiceIdType serviceIdType) throws IOException {
    return this.pushServiceSocket.getCurrentSignedPreKey(serviceIdType);
  }

  /**
   * @return True if the identifier corresponds to a registered user, otherwise false.
   */
  public boolean isIdentifierRegistered(ServiceId identifier) throws IOException {
    return pushServiceSocket.isIdentifierRegistered(identifier);
  }

  @SuppressWarnings("SameParameterValue")
  public Map<String, ACI> getRegisteredUsers(KeyStore iasKeyStore, Set<String> e164numbers, String mrenclave)
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

      HashMap<String, ACI> results         = new HashMap<>(addressBook.size());
      DataInputStream      uuidInputStream = new DataInputStream(new ByteArrayInputStream(data));

      for (String candidate : addressBook) {
        long candidateUuidHigh = uuidInputStream.readLong();
        long candidateUuidLow  = uuidInputStream.readLong();
        if (candidateUuidHigh != 0 || candidateUuidLow != 0) {
          results.put('+' + candidate, ACI.from(new UUID(candidateUuidHigh, candidateUuidLow)));
        }
      }

      return results;
    } catch (InvalidCiphertextException e) {
      throw new UnauthenticatedResponseException(e);
    }
  }

  public CdsiV2Service.Response getRegisteredUsersWithCdsi(Set<String> previousE164s,
                                                           Set<String> newE164s,
                                                           Map<ServiceId, ProfileKey> serviceIds,
                                                           boolean requireAcis,
                                                           Optional<byte[]> token,
                                                           String mrEnclave,
                                                           Consumer<byte[]> tokenSaver)
      throws IOException
  {
    CdsiAuthResponse                                auth    = pushServiceSocket.getCdsiAuth();
    CdsiV2Service                                   service = new CdsiV2Service(configuration, mrEnclave);
    CdsiV2Service.Request                           request = new CdsiV2Service.Request(previousE164s, newE164s, serviceIds, requireAcis, token);
    Single<ServiceResponse<CdsiV2Service.Response>> single  = service.getRegisteredUsers(auth.getUsername(), auth.getPassword(), request, tokenSaver);

    ServiceResponse<CdsiV2Service.Response> serviceResponse;
    try {
      serviceResponse = single.blockingGet();
    } catch (RuntimeException e) {
      Throwable cause = e.getCause();
      if (cause instanceof InterruptedException) {
        throw new IOException("Interrupted", cause);
      } else {
        throw e;
      }
    } catch (Exception e) {
      throw new RuntimeException("Unexpected exception when retrieving registered users!", e);
    }

    if (serviceResponse.getResult().isPresent()) {
      return serviceResponse.getResult().get();
    } else if (serviceResponse.getApplicationError().isPresent()) {
      if (serviceResponse.getApplicationError().get() instanceof IOException) {
        throw (IOException) serviceResponse.getApplicationError().get();
      } else {
        throw new IOException(serviceResponse.getApplicationError().get());
      }
    } else if (serviceResponse.getExecutionError().isPresent()) {
      throw new IOException(serviceResponse.getExecutionError().get());
    } else {
      throw new IOException("Missing result!");
    }
  }


  public Optional<SignalStorageManifest> getStorageManifest(StorageKey storageKey) throws IOException {
    try {
      String          authToken       = this.pushServiceSocket.getStorageAuth();
      StorageManifest storageManifest = this.pushServiceSocket.getStorageManifest(authToken);

      return Optional.of(SignalStorageModels.remoteToLocalStorageManifest(storageManifest, storageKey));
    } catch (InvalidKeyException | NotFoundException e) {
      Log.w(TAG, "Error while fetching manifest.", e);
      return Optional.empty();
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
        return Optional.empty();
      }

      return Optional.of(SignalStorageModels.remoteToLocalStorageManifest(storageManifest, storageKey));
    } catch (NoContentException e) {
      return Optional.empty();
    }
  }

  public List<SignalStorageRecord> readStorageRecords(StorageKey storageKey, List<StorageId> storageKeys) throws IOException, InvalidKeyException {
    if (storageKeys.isEmpty()) {
      return Collections.emptyList();
    }

    List<SignalStorageRecord> result           = new ArrayList<>();
    Map<ByteString, Integer>  typeMap          = new HashMap<>();
    List<ReadOperation>       readOperations   = new LinkedList<>();
    ReadOperation.Builder     currentOperation = ReadOperation.newBuilder();

    for (StorageId key : storageKeys) {
      typeMap.put(ByteString.copyFrom(key.getRaw()), key.getType());

      if (currentOperation.getReadKeyCount() >= STORAGE_READ_MAX_ITEMS) {
        Log.i(TAG, "Going over max read items. Starting a new read operation.");
        readOperations.add(currentOperation.build());
        currentOperation = ReadOperation.newBuilder();
      }

      if (StorageId.isKnownType(key.getType())) {
        currentOperation.addReadKey(ByteString.copyFrom(key.getRaw()));
      } else {
        result.add(SignalStorageRecord.forUnknown(key));
      }
    }

    if (currentOperation.getReadKeyCount() > 0) {
      readOperations.add(currentOperation.build());
    }

    Log.i(TAG, "Reading " + storageKeys.size() + " items split over " + readOperations.size() + " page(s).");

    String authToken = this.pushServiceSocket.getStorageAuth();

    for (ReadOperation readOperation : readOperations) {
      StorageItems items = this.pushServiceSocket.readStorageItems(authToken, readOperation);

      for (StorageItem item : items.getItemsList()) {
        Integer type = typeMap.get(item.getKey());
        if (type != null) {
          result.add(SignalStorageModels.remoteToLocalStorageRecord(item, type, storageKey));
        } else {
          Log.w(TAG, "No type found! Skipping.");
        }
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
                                                        .setTypeValue(id.getType()).build();
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
        ids.add(StorageId.forType(id.getRaw().toByteArray(), id.getTypeValue()));
      }

      SignalStorageManifest conflictManifest = new SignalStorageManifest(record.getVersion(), ids);

      return Optional.of(conflictManifest);
    } else {
      return Optional.empty();
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
                        IdentityKeyPair aciIdentityKeyPair,
                        IdentityKeyPair pniIdentityKeyPair,
                        ProfileKey profileKey,
                        String code)
      throws InvalidKeyException, IOException
  {
    String e164 = credentials.getE164();
    ACI    aci  = credentials.getAci();
    PNI    pni  = credentials.getPni();

    Preconditions.checkArgument(e164 != null, "Missing e164!");
    Preconditions.checkArgument(aci != null, "Missing ACI!");
    Preconditions.checkArgument(pni != null, "Missing PNI!");

    PrimaryProvisioningCipher cipher  = new PrimaryProvisioningCipher(deviceKey);
    ProvisionMessage.Builder  message = ProvisionMessage.newBuilder()
                                                        .setAciIdentityKeyPublic(ByteString.copyFrom(aciIdentityKeyPair.getPublicKey().serialize()))
                                                        .setAciIdentityKeyPrivate(ByteString.copyFrom(aciIdentityKeyPair.getPrivateKey().serialize()))
                                                        .setPniIdentityKeyPublic(ByteString.copyFrom(pniIdentityKeyPair.getPublicKey().serialize()))
                                                        .setPniIdentityKeyPrivate(ByteString.copyFrom(pniIdentityKeyPair.getPrivateKey().serialize()))
                                                        .setAci(aci.toString())
                                                        .setPni(pni.toString())
                                                        .setNumber(e164)
                                                        .setProfileKey(ByteString.copyFrom(profileKey.serialize()))
                                                        .setProvisioningCode(code)
                                                        .setProvisioningVersion(ProvisioningVersion.CURRENT_VALUE);

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

  public void checkNetworkConnection() throws IOException {
    this.pushServiceSocket.pingStorageService();
  }

  public CurrencyConversions getCurrencyConversions() throws IOException {
    return this.pushServiceSocket.getCurrencyConversions();
  }

  public void reportSpam(ServiceId serviceId, String serverGuid) throws IOException {
    this.pushServiceSocket.reportSpam(serviceId, serverGuid);
  }

  /**
   * @return The avatar URL path, if one was written.
   */
  public Optional<String> setVersionedProfile(ACI aci,
                                              ProfileKey profileKey,
                                              String name,
                                              String about,
                                              String aboutEmoji,
                                              Optional<SignalServiceProtos.PaymentAddress> paymentsAddress,
                                              AvatarUploadParams avatar,
                                              List<String> visibleBadgeIds)
      throws IOException
  {
    if (name == null) name = "";

    ProfileCipher     profileCipher               = new ProfileCipher(profileKey);
    byte[]            ciphertextName              = profileCipher.encryptString(name, ProfileCipher.getTargetNameLength(name));
    byte[]            ciphertextAbout             = profileCipher.encryptString(about, ProfileCipher.getTargetAboutLength(about));
    byte[]            ciphertextEmoji             = profileCipher.encryptString(aboutEmoji, ProfileCipher.EMOJI_PADDED_LENGTH);
    byte[]            ciphertextMobileCoinAddress = paymentsAddress.map(address -> profileCipher.encryptWithLength(address.toByteArray(), ProfileCipher.PAYMENTS_ADDRESS_CONTENT_SIZE)).orElse(null);
    ProfileAvatarData profileAvatarData           = null;

    if (avatar.stream != null && !avatar.keepTheSame) {
      profileAvatarData = new ProfileAvatarData(avatar.stream.getStream(),
                                                ProfileCipherOutputStream.getCiphertextLength(avatar.stream.getLength()),
                                                avatar.stream.getContentType(),
                                                new ProfileCipherOutputStreamFactory(profileKey));
    }

    return this.pushServiceSocket.writeProfile(new SignalServiceProfileWrite(profileKey.getProfileKeyVersion(aci.uuid()).serialize(),
                                                                             ciphertextName,
                                                                             ciphertextAbout,
                                                                             ciphertextEmoji,
                                                                             ciphertextMobileCoinAddress,
                                                                             avatar.hasAvatar,
                                                                             avatar.keepTheSame,
                                                                             profileKey.getCommitment(aci.uuid()).serialize(),
                                                                             visibleBadgeIds),
                                                                             profileAvatarData);
  }

  public Optional<ExpiringProfileKeyCredential> resolveProfileKeyCredential(ServiceId serviceId, ProfileKey profileKey, Locale locale)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    try {
      ProfileAndCredential credential = this.pushServiceSocket.retrieveVersionedProfileAndCredential(serviceId.uuid(), profileKey, Optional.empty(), locale).get(10, TimeUnit.SECONDS);
      return credential.getExpiringProfileKeyCredential();
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

  public ACI getAciByUsername(String username) throws IOException {
    return this.pushServiceSocket.getAciByUsername(username);
  }

  public void setUsername(String nickname, String existingUsername) throws IOException {
    this.pushServiceSocket.setUsername(nickname, existingUsername);
  }

  public ReserveUsernameResponse reserveUsername(String nickname) throws IOException {
    return this.pushServiceSocket.reserveUsername(nickname);
  }

  public void confirmUsername(ReserveUsernameResponse reserveUsernameResponse) throws IOException {
    this.pushServiceSocket.confirmUsername(reserveUsernameResponse);
  }

  public void deleteUsername() throws IOException {
    this.pushServiceSocket.deleteUsername();
  }

  public void deleteAccount() throws IOException {
    this.pushServiceSocket.deleteAccount();
  }

  public void requestRateLimitPushChallenge() throws IOException {
    this.pushServiceSocket.requestRateLimitPushChallenge();
  }

  public void submitRateLimitPushChallenge(String challenge) throws IOException {
    this.pushServiceSocket.submitRateLimitPushChallenge(challenge);
  }

  public void submitRateLimitRecaptchaChallenge(String challenge, String recaptchaToken) throws IOException {
    this.pushServiceSocket.submitRateLimitRecaptchaChallenge(challenge, recaptchaToken);
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

  public AuthCredentials getPaymentsAuthorization() throws IOException {
    return pushServiceSocket.getPaymentsAuthorization();
  }

}
