/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.signal.zkgroup.ServerPublicParams;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.signal.zkgroup.profiles.ProfileKeyCredentialRequest;
import org.signal.zkgroup.profiles.ProfileKeyCredentialRequestContext;
import org.signal.zkgroup.profiles.ProfileKeyVersion;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.FeatureFlags;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener;
import org.whispersystems.signalservice.api.messages.calls.TurnServerInfo;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfileWrite;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.CaptchaRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.ContactManifestMismatchException;
import org.whispersystems.signalservice.api.push.exceptions.ExpectationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.NoContentException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;
import org.whispersystems.signalservice.api.push.exceptions.RemoteAttestationResponseExpiredException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.push.exceptions.UsernameMalformedException;
import org.whispersystems.signalservice.api.push.exceptions.UsernameTakenException;
import org.whispersystems.signalservice.api.storage.StorageAuthResponse;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.Tls12SocketFactory;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.configuration.SignalUrl;
import org.whispersystems.signalservice.internal.contacts.entities.DiscoveryRequest;
import org.whispersystems.signalservice.internal.contacts.entities.DiscoveryResponse;
import org.whispersystems.signalservice.internal.contacts.entities.KeyBackupRequest;
import org.whispersystems.signalservice.internal.contacts.entities.KeyBackupResponse;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;
import org.whispersystems.signalservice.internal.groupsv2.ClientZkOperations;
import org.whispersystems.signalservice.internal.push.exceptions.MismatchedDevicesException;
import org.whispersystems.signalservice.internal.push.exceptions.StaleDevicesException;
import org.whispersystems.signalservice.internal.push.http.CancelationSignal;
import org.whispersystems.signalservice.internal.push.http.DigestingRequestBody;
import org.whispersystems.signalservice.internal.push.http.OutputStreamFactory;
import org.whispersystems.signalservice.internal.storage.protos.ReadOperation;
import org.whispersystems.signalservice.internal.storage.protos.StorageItems;
import org.whispersystems.signalservice.internal.storage.protos.StorageManifest;
import org.whispersystems.signalservice.internal.storage.protos.WriteOperation;
import org.whispersystems.signalservice.internal.util.BlacklistingTrustManager;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * @author Moxie Marlinspike
 */
public class PushServiceSocket {

  private static final String TAG = PushServiceSocket.class.getSimpleName();

  private static final String CREATE_ACCOUNT_SMS_PATH   = "/v1/accounts/sms/code/%s?client=%s";
  private static final String CREATE_ACCOUNT_VOICE_PATH = "/v1/accounts/voice/code/%s";
  private static final String VERIFY_ACCOUNT_CODE_PATH  = "/v1/accounts/code/%s";
  private static final String REGISTER_GCM_PATH         = "/v1/accounts/gcm/";
  private static final String TURN_SERVER_INFO          = "/v1/accounts/turn";
  private static final String SET_ACCOUNT_ATTRIBUTES    = "/v1/accounts/attributes/";
  private static final String PIN_PATH                  = "/v1/accounts/pin/";
  private static final String REGISTRATION_LOCK_PATH    = "/v1/accounts/registration_lock";
  private static final String REQUEST_PUSH_CHALLENGE    = "/v1/accounts/fcm/preauth/%s/%s";
  private static final String WHO_AM_I                  = "/v1/accounts/whoami";
  private static final String SET_USERNAME_PATH         = "/v1/accounts/username/%s";
  private static final String DELETE_USERNAME_PATH      = "/v1/accounts/username";

  private static final String PREKEY_METADATA_PATH      = "/v2/keys/";
  private static final String PREKEY_PATH               = "/v2/keys/%s";
  private static final String PREKEY_DEVICE_PATH        = "/v2/keys/%s/%s";
  private static final String SIGNED_PREKEY_PATH        = "/v2/keys/signed";

  private static final String PROVISIONING_CODE_PATH    = "/v1/devices/provisioning/code";
  private static final String PROVISIONING_MESSAGE_PATH = "/v1/provisioning/%s";
  private static final String DEVICE_PATH               = "/v1/devices/%s";

  private static final String DIRECTORY_TOKENS_PATH     = "/v1/directory/tokens";
  private static final String DIRECTORY_VERIFY_PATH     = "/v1/directory/%s";
  private static final String DIRECTORY_AUTH_PATH       = "/v1/directory/auth";
  private static final String DIRECTORY_FEEDBACK_PATH   = "/v1/directory/feedback-v3/%s";
  private static final String MESSAGE_PATH              = "/v1/messages/%s";
  private static final String SENDER_ACK_MESSAGE_PATH   = "/v1/messages/%s/%d";
  private static final String UUID_ACK_MESSAGE_PATH     = "/v1/messages/uuid/%s";
  private static final String ATTACHMENT_PATH           = "/v2/attachments/form/upload";

  private static final String PROFILE_PATH              = "/v1/profile/%s";
  private static final String PROFILE_USERNAME_PATH     = "/v1/profile/username/%s";

  private static final String SENDER_CERTIFICATE_LEGACY_PATH = "/v1/certificate/delivery";
  private static final String SENDER_CERTIFICATE_PATH        = "/v1/certificate/delivery?includeUuid=true";

  private static final String KBS_AUTH_PATH             = "/v1/backup/auth";

  private static final String ATTACHMENT_DOWNLOAD_PATH  = "attachments/%d";
  private static final String ATTACHMENT_UPLOAD_PATH    = "attachments/";

  private static final String STICKER_MANIFEST_PATH     = "stickers/%s/manifest.proto";
  private static final String STICKER_PATH              = "stickers/%s/full/%d";

  private static final Map<String, String> NO_HEADERS = Collections.emptyMap();
  private static final ResponseCodeHandler NO_HANDLER = new EmptyResponseCodeHandler();

  private       long      soTimeoutMillis = TimeUnit.SECONDS.toMillis(30);
  private final Set<Call> connections     = new HashSet<>();

  private final ServiceConnectionHolder[]  serviceClients;
  private final ConnectionHolder[]         cdnClients;
  private final ConnectionHolder[]         contactDiscoveryClients;
  private final ConnectionHolder[]         keyBackupServiceClients;
  private final ConnectionHolder[]         storageClients;

  private final CredentialsProvider credentialsProvider;
  private final String              signalAgent;
  private final SecureRandom        random;
  private final ClientZkOperations  clientZkOperations;

  public PushServiceSocket(SignalServiceConfiguration signalServiceConfiguration, CredentialsProvider credentialsProvider, String signalAgent) {
    this.credentialsProvider               = credentialsProvider;
    this.signalAgent                       = signalAgent;
    this.serviceClients                    = createServiceConnectionHolders(signalServiceConfiguration.getSignalServiceUrls(), signalServiceConfiguration.getNetworkInterceptors());
    this.cdnClients                        = createConnectionHolders(signalServiceConfiguration.getSignalCdnUrls(), signalServiceConfiguration.getNetworkInterceptors());
    this.contactDiscoveryClients           = createConnectionHolders(signalServiceConfiguration.getSignalContactDiscoveryUrls(), signalServiceConfiguration.getNetworkInterceptors());
    this.keyBackupServiceClients           = createConnectionHolders(signalServiceConfiguration.getSignalKeyBackupServiceUrls(), signalServiceConfiguration.getNetworkInterceptors());
    this.storageClients                    = createConnectionHolders(signalServiceConfiguration.getSignalStorageUrls(), signalServiceConfiguration.getNetworkInterceptors());
    this.random                            = new SecureRandom();
    this.clientZkOperations                = FeatureFlags.ZK_GROUPS ? new ClientZkOperations(new ServerPublicParams(signalServiceConfiguration.getZkGroupServerPublicParams())) : null;
  }

  public void requestSmsVerificationCode(boolean androidSmsRetriever, Optional<String> captchaToken, Optional<String> challenge) throws IOException {
    String path = String.format(CREATE_ACCOUNT_SMS_PATH, credentialsProvider.getE164(), androidSmsRetriever ? "android-ng" : "android");

    if (captchaToken.isPresent()) {
      path += "&captcha=" + captchaToken.get();
    } else if (challenge.isPresent()) {
      path += "&challenge=" + challenge.get();
    }

    makeServiceRequest(path, "GET", null, NO_HEADERS, new ResponseCodeHandler() {
      @Override
      public void handle(int responseCode) throws NonSuccessfulResponseCodeException {
        if (responseCode == 402) {
          throw new CaptchaRequiredException();
        }
      }
    });
  }

  public void requestVoiceVerificationCode(Locale locale, Optional<String> captchaToken, Optional<String> challenge) throws IOException {
    Map<String, String> headers = locale != null ? Collections.singletonMap("Accept-Language", locale.getLanguage() + "-" + locale.getCountry()) : NO_HEADERS;
    String              path    = String.format(CREATE_ACCOUNT_VOICE_PATH, credentialsProvider.getE164());

    if (captchaToken.isPresent()) {
      path += "?captcha=" + captchaToken.get();
    } else if (challenge.isPresent()) {
      path += "?challenge=" + challenge.get();
    }

    makeServiceRequest(path, "GET", null, headers, new ResponseCodeHandler() {
      @Override
      public void handle(int responseCode) throws NonSuccessfulResponseCodeException {
        if (responseCode == 402) {
          throw new CaptchaRequiredException();
        }
      }
    });
  }

  public UUID getOwnUuid() throws IOException {
    String         body     = makeServiceRequest(WHO_AM_I, "GET", null);
    WhoAmIResponse response = JsonUtil.fromJson(body, WhoAmIResponse.class);
    Optional<UUID> uuid     = UuidUtil.parse(response.getUuid());

    if (uuid.isPresent()) {
      return uuid.get();
    } else {
      throw new IOException("Invalid UUID!");
    }
  }

  public UUID verifyAccountCode(String verificationCode, String signalingKey, int registrationId, boolean fetchesMessages,
                                String pin, String registrationLock,
                                byte[] unidentifiedAccessKey, boolean unrestrictedUnidentifiedAccess)
      throws IOException
  {
    AccountAttributes     signalingKeyEntity = new AccountAttributes(signalingKey, registrationId, fetchesMessages, pin, registrationLock, unidentifiedAccessKey, unrestrictedUnidentifiedAccess);
    String                requestBody        = JsonUtil.toJson(signalingKeyEntity);
    String                responseBody       = makeServiceRequest(String.format(VERIFY_ACCOUNT_CODE_PATH, verificationCode), "PUT", requestBody);
    VerifyAccountResponse response           = JsonUtil.fromJson(responseBody, VerifyAccountResponse.class);
    Optional<UUID>        uuid               = UuidUtil.parse(response.getUuid());

    if (uuid.isPresent()) {
      return uuid.get();
    } else {
      throw new IOException("Invalid UUID!");
    }
  }

  public void setAccountAttributes(String signalingKey, int registrationId, boolean fetchesMessages,
                                   String pin, String registrationLock,
                                   byte[] unidentifiedAccessKey, boolean unrestrictedUnidentifiedAccess)
      throws IOException
  {
    if (registrationLock != null && pin != null) {
      throw new AssertionError("Pin should be null if registrationLock is set.");
    }

    AccountAttributes accountAttributes = new AccountAttributes(signalingKey, registrationId, fetchesMessages, pin, registrationLock,
                                                                unidentifiedAccessKey, unrestrictedUnidentifiedAccess);
    makeServiceRequest(SET_ACCOUNT_ATTRIBUTES, "PUT", JsonUtil.toJson(accountAttributes));
  }

  public String getNewDeviceVerificationCode() throws IOException {
    String responseText = makeServiceRequest(PROVISIONING_CODE_PATH, "GET", null);
    return JsonUtil.fromJson(responseText, DeviceCode.class).getVerificationCode();
  }

  public List<DeviceInfo> getDevices() throws IOException {
    String responseText = makeServiceRequest(String.format(DEVICE_PATH, ""), "GET", null);
    return JsonUtil.fromJson(responseText, DeviceInfoList.class).getDevices();
  }

  public void removeDevice(long deviceId) throws IOException {
    makeServiceRequest(String.format(DEVICE_PATH, String.valueOf(deviceId)), "DELETE", null);
  }

  public void sendProvisioningMessage(String destination, byte[] body) throws IOException {
    makeServiceRequest(String.format(PROVISIONING_MESSAGE_PATH, destination), "PUT",
                       JsonUtil.toJson(new ProvisioningMessage(Base64.encodeBytes(body))));
  }

  public void registerGcmId(String gcmRegistrationId) throws IOException {
    GcmRegistrationId registration = new GcmRegistrationId(gcmRegistrationId, true);
    makeServiceRequest(REGISTER_GCM_PATH, "PUT", JsonUtil.toJson(registration));
  }

  public void unregisterGcmId() throws IOException {
    makeServiceRequest(REGISTER_GCM_PATH, "DELETE", null);
  }

  public void requestPushChallenge(String gcmRegistrationId, String e164number) throws IOException {
    makeServiceRequest(String.format(Locale.US, REQUEST_PUSH_CHALLENGE, gcmRegistrationId, e164number), "GET", null);
  }

  /** Note: Setting a KBS Pin will clear this */
  public void removePin() throws IOException {
    makeServiceRequest(PIN_PATH, "DELETE", null);
  }

  public void setRegistrationLock(String registrationLock) throws IOException {
    RegistrationLockV2 accountLock = new RegistrationLockV2(registrationLock);
    makeServiceRequest(REGISTRATION_LOCK_PATH, "PUT", JsonUtil.toJson(accountLock));
  }

  public void removePinV2() throws IOException {
    makeServiceRequest(REGISTRATION_LOCK_PATH, "DELETE", null);
  }

  public byte[] getSenderCertificateLegacy() throws IOException {
    String responseText = makeServiceRequest(SENDER_CERTIFICATE_LEGACY_PATH, "GET", null);
    return JsonUtil.fromJson(responseText, SenderCertificate.class).getCertificate();
  }

  public byte[] getSenderCertificate() throws IOException {
    String responseText = makeServiceRequest(SENDER_CERTIFICATE_PATH, "GET", null);
    return JsonUtil.fromJson(responseText, SenderCertificate.class).getCertificate();
  }

  public SendMessageResponse sendMessage(OutgoingPushMessageList bundle, Optional<UnidentifiedAccess> unidentifiedAccess)
      throws IOException
  {
    try {
      String responseText = makeServiceRequest(String.format(MESSAGE_PATH, bundle.getDestination()), "PUT", JsonUtil.toJson(bundle), NO_HEADERS, unidentifiedAccess);

      if (responseText == null) return new SendMessageResponse(false);
      else                      return JsonUtil.fromJson(responseText, SendMessageResponse.class);
    } catch (NotFoundException nfe) {
      throw new UnregisteredUserException(bundle.getDestination(), nfe);
    }
  }

  public List<SignalServiceEnvelopeEntity> getMessages() throws IOException {
    String responseText = makeServiceRequest(String.format(MESSAGE_PATH, ""), "GET", null);
    return JsonUtil.fromJson(responseText, SignalServiceEnvelopeEntityList.class).getMessages();
  }

  public void acknowledgeMessage(String sender, long timestamp) throws IOException {
    makeServiceRequest(String.format(Locale.US, SENDER_ACK_MESSAGE_PATH, sender, timestamp), "DELETE", null);
  }

  public void acknowledgeMessage(String uuid) throws IOException {
    makeServiceRequest(String.format(UUID_ACK_MESSAGE_PATH, uuid), "DELETE", null);
  }

  public void registerPreKeys(IdentityKey identityKey,
                              SignedPreKeyRecord signedPreKey,
                              List<PreKeyRecord> records)
      throws IOException
  {
    List<PreKeyEntity> entities = new LinkedList<>();

    for (PreKeyRecord record : records) {
      PreKeyEntity entity = new PreKeyEntity(record.getId(),
                                             record.getKeyPair().getPublicKey());

      entities.add(entity);
    }

    SignedPreKeyEntity signedPreKeyEntity = new SignedPreKeyEntity(signedPreKey.getId(),
                                                                   signedPreKey.getKeyPair().getPublicKey(),
                                                                   signedPreKey.getSignature());

    makeServiceRequest(String.format(PREKEY_PATH, ""), "PUT",
                       JsonUtil.toJson(new PreKeyState(entities, signedPreKeyEntity, identityKey)));
  }

  public int getAvailablePreKeys() throws IOException {
    String       responseText = makeServiceRequest(PREKEY_METADATA_PATH, "GET", null);
    PreKeyStatus preKeyStatus = JsonUtil.fromJson(responseText, PreKeyStatus.class);

    return preKeyStatus.getCount();
  }

  public List<PreKeyBundle> getPreKeys(SignalServiceAddress destination,
                                       Optional<UnidentifiedAccess> unidentifiedAccess,
                                       int deviceIdInteger)
      throws IOException
  {
    try {
      String deviceId = String.valueOf(deviceIdInteger);

      if (deviceId.equals("1"))
        deviceId = "*";

      String path = String.format(PREKEY_DEVICE_PATH, destination.getIdentifier(), deviceId);

      if (destination.getRelay().isPresent()) {
        path = path + "?relay=" + destination.getRelay().get();
      }

      String             responseText = makeServiceRequest(path, "GET", null, NO_HEADERS, unidentifiedAccess);
      PreKeyResponse     response     = JsonUtil.fromJson(responseText, PreKeyResponse.class);
      List<PreKeyBundle> bundles      = new LinkedList<>();

      for (PreKeyResponseItem device : response.getDevices()) {
        ECPublicKey preKey                = null;
        ECPublicKey signedPreKey          = null;
        byte[]      signedPreKeySignature = null;
        int         preKeyId              = -1;
        int         signedPreKeyId        = -1;

        if (device.getSignedPreKey() != null) {
          signedPreKey          = device.getSignedPreKey().getPublicKey();
          signedPreKeyId        = device.getSignedPreKey().getKeyId();
          signedPreKeySignature = device.getSignedPreKey().getSignature();
        }

        if (device.getPreKey() != null) {
          preKeyId = device.getPreKey().getKeyId();
          preKey   = device.getPreKey().getPublicKey();
        }

        bundles.add(new PreKeyBundle(device.getRegistrationId(), device.getDeviceId(), preKeyId,
                                     preKey, signedPreKeyId, signedPreKey, signedPreKeySignature,
                                     response.getIdentityKey()));
      }

      return bundles;
    } catch (NotFoundException nfe) {
      throw new UnregisteredUserException(destination.getIdentifier(), nfe);
    }
  }

  public PreKeyBundle getPreKey(SignalServiceAddress destination, int deviceId) throws IOException {
    try {
      String path = String.format(PREKEY_DEVICE_PATH, destination.getIdentifier(), String.valueOf(deviceId));

      if (destination.getRelay().isPresent()) {
        path = path + "?relay=" + destination.getRelay().get();
      }

      String         responseText = makeServiceRequest(path, "GET", null);
      PreKeyResponse response     = JsonUtil.fromJson(responseText, PreKeyResponse.class);

      if (response.getDevices() == null || response.getDevices().size() < 1)
        throw new IOException("Empty prekey list");

      PreKeyResponseItem device                = response.getDevices().get(0);
      ECPublicKey        preKey                = null;
      ECPublicKey        signedPreKey          = null;
      byte[]             signedPreKeySignature = null;
      int                preKeyId              = -1;
      int                signedPreKeyId        = -1;

      if (device.getPreKey() != null) {
        preKeyId = device.getPreKey().getKeyId();
        preKey   = device.getPreKey().getPublicKey();
      }

      if (device.getSignedPreKey() != null) {
        signedPreKeyId        = device.getSignedPreKey().getKeyId();
        signedPreKey          = device.getSignedPreKey().getPublicKey();
        signedPreKeySignature = device.getSignedPreKey().getSignature();
      }

      return new PreKeyBundle(device.getRegistrationId(), device.getDeviceId(), preKeyId, preKey,
                              signedPreKeyId, signedPreKey, signedPreKeySignature, response.getIdentityKey());
    } catch (NotFoundException nfe) {
      throw new UnregisteredUserException(destination.getIdentifier(), nfe);
    }
  }

  public SignedPreKeyEntity getCurrentSignedPreKey() throws IOException {
    try {
      String responseText = makeServiceRequest(SIGNED_PREKEY_PATH, "GET", null);
      return JsonUtil.fromJson(responseText, SignedPreKeyEntity.class);
    } catch (NotFoundException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  public void setCurrentSignedPreKey(SignedPreKeyRecord signedPreKey) throws IOException {
    SignedPreKeyEntity signedPreKeyEntity = new SignedPreKeyEntity(signedPreKey.getId(),
                                                                   signedPreKey.getKeyPair().getPublicKey(),
                                                                   signedPreKey.getSignature());
    makeServiceRequest(SIGNED_PREKEY_PATH, "PUT", JsonUtil.toJson(signedPreKeyEntity));
  }

  public void retrieveAttachment(long attachmentId, File destination, int maxSizeBytes, ProgressListener listener)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    downloadFromCdn(destination, String.format(Locale.US, ATTACHMENT_DOWNLOAD_PATH, attachmentId), maxSizeBytes, listener);
  }

  public void retrieveSticker(File destination, byte[] packId, int stickerId)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    String hexPackId = Hex.toStringCondensed(packId);
    downloadFromCdn(destination, String.format(Locale.US, STICKER_PATH, hexPackId, stickerId), 1024 * 1024, null);
  }

  public byte[] retrieveSticker(byte[] packId, int stickerId)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    String                hexPackId = Hex.toStringCondensed(packId);
    ByteArrayOutputStream output    = new ByteArrayOutputStream();

    downloadFromCdn(output, 0, String.format(Locale.US, STICKER_PATH, hexPackId, stickerId), 1024 * 1024, null);

    return output.toByteArray();
  }

  public byte[] retrieveStickerManifest(byte[] packId)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    String                hexPackId = Hex.toStringCondensed(packId);
    ByteArrayOutputStream output    = new ByteArrayOutputStream();

    downloadFromCdn(output, 0, String.format(STICKER_MANIFEST_PATH, hexPackId), 1024 * 1024, null);

    return output.toByteArray();
  }

  public SignalServiceProfile retrieveProfile(SignalServiceAddress target, Optional<UnidentifiedAccess> unidentifiedAccess)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    String response = makeServiceRequest(String.format(PROFILE_PATH, target.getIdentifier()), "GET", null, NO_HEADERS, unidentifiedAccess);

    try {
      return JsonUtil.fromJson(response, SignalServiceProfile.class);
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new NonSuccessfulResponseCodeException("Unable to parse entity");
    }
  }

  public SignalServiceProfile retrieveProfileByUsername(String username, Optional<UnidentifiedAccess> unidentifiedAccess)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    String response = makeServiceRequest(String.format(PROFILE_USERNAME_PATH, username), "GET", null, NO_HEADERS, unidentifiedAccess);

    try {
      return JsonUtil.fromJson(response, SignalServiceProfile.class);
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new NonSuccessfulResponseCodeException("Unable to parse entity");
    }
  }

  public ProfileAndCredential retrieveProfile(UUID target, ProfileKey profileKey, Optional<UnidentifiedAccess> unidentifiedAccess)
    throws NonSuccessfulResponseCodeException, VerificationFailedException
  {
    if (!FeatureFlags.VERSIONED_PROFILES) {
      throw new AssertionError();
    }

    try {
      ProfileKeyVersion                  profileKeyIdentifier = profileKey.getProfileKeyVersion();
      ProfileKeyCredentialRequestContext requestContext       = clientZkOperations.getProfileOperations().createProfileKeyCredentialRequestContext(random, target, profileKey);
      ProfileKeyCredentialRequest        request              = requestContext.getRequest();

      String version           = profileKeyIdentifier.serialize();
      String credentialRequest = Hex.toStringCondensed(request.serialize());
      String subPath           = String.format("%s/%s/%s", target, version, credentialRequest);

      String response = makeServiceRequest(String.format(PROFILE_PATH, subPath), "GET", null, NO_HEADERS, unidentifiedAccess);

      SignalServiceProfile signalServiceProfile = JsonUtil.fromJson(response, SignalServiceProfile.class);

      ProfileKeyCredential profileKeyCredential = signalServiceProfile.getProfileKeyCredentialResponse() != null
                                                ? clientZkOperations.getProfileOperations().receiveProfileKeyCredential(requestContext, signalServiceProfile.getProfileKeyCredentialResponse())
                                                : null;

      return new ProfileAndCredential(signalServiceProfile, SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL, Optional.fromNullable(profileKeyCredential));
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new NonSuccessfulResponseCodeException("Unable to parse entity");
    }
  }

  public void retrieveProfileAvatar(String path, File destination, int maxSizeBytes)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    downloadFromCdn(destination, path, maxSizeBytes, null);
  }

  public void setProfileName(String name) throws NonSuccessfulResponseCodeException, PushNetworkException {
    if (FeatureFlags.VERSIONED_PROFILES) {
      throw new AssertionError();
    }

    makeServiceRequest(String.format(PROFILE_PATH, "name/" + (name == null ? "" : URLEncoder.encode(name))), "PUT", "");
  }

  public void setProfileAvatar(ProfileAvatarData profileAvatar)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    if (FeatureFlags.VERSIONED_PROFILES) {
      throw new AssertionError();
    }

    String                        response       = makeServiceRequest(String.format(PROFILE_PATH, "form/avatar"), "GET", null);
    ProfileAvatarUploadAttributes formAttributes;

    try {
      formAttributes = JsonUtil.fromJson(response, ProfileAvatarUploadAttributes.class);
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new NonSuccessfulResponseCodeException("Unable to parse entity");
    }

    if (profileAvatar != null) {
      uploadToCdn("", formAttributes.getAcl(), formAttributes.getKey(),
                  formAttributes.getPolicy(), formAttributes.getAlgorithm(),
                  formAttributes.getCredential(), formAttributes.getDate(),
                  formAttributes.getSignature(), profileAvatar.getData(),
                  profileAvatar.getContentType(), profileAvatar.getDataLength(),
                  profileAvatar.getOutputStreamFactory(), null, null);
    }
  }

  public void writeProfile(SignalServiceProfileWrite signalServiceProfileWrite, ProfileAvatarData profileAvatar)
    throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    if (!FeatureFlags.VERSIONED_PROFILES) {
      throw new AssertionError();
    }

    String                        requestBody    = JsonUtil.toJson(signalServiceProfileWrite);
    ProfileAvatarUploadAttributes formAttributes;

    String response = makeServiceRequest(String.format(PROFILE_PATH, ""), "PUT", requestBody);

    if (signalServiceProfileWrite.hasAvatar() && profileAvatar != null) {
       try {
        formAttributes = JsonUtil.fromJson(response, ProfileAvatarUploadAttributes.class);
      } catch (IOException e) {
        Log.w(TAG, e);
        throw new NonSuccessfulResponseCodeException("Unable to parse entity");
      }

      uploadToCdn("", formAttributes.getAcl(), formAttributes.getKey(),
                  formAttributes.getPolicy(), formAttributes.getAlgorithm(),
                  formAttributes.getCredential(), formAttributes.getDate(),
                  formAttributes.getSignature(), profileAvatar.getData(),
                  profileAvatar.getContentType(), profileAvatar.getDataLength(),
                  profileAvatar.getOutputStreamFactory(), null, null);
    }
  }

  public void setUsername(String username) throws IOException {
    makeServiceRequest(String.format(SET_USERNAME_PATH, username), "PUT", "", NO_HEADERS, new ResponseCodeHandler() {
      @Override
      public void handle(int responseCode) throws NonSuccessfulResponseCodeException {
        switch (responseCode) {
          case 400: throw new UsernameMalformedException();
          case 409: throw new UsernameTakenException();
        }
      }
    }, Optional.<UnidentifiedAccess>absent());
  }

  public void deleteUsername() throws IOException {
    makeServiceRequest(DELETE_USERNAME_PATH, "DELETE", null);
  }

  public List<ContactTokenDetails> retrieveDirectory(Set<String> contactTokens)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    try {
      ContactTokenList        contactTokenList = new ContactTokenList(new LinkedList<>(contactTokens));
      String                  response         = makeServiceRequest(DIRECTORY_TOKENS_PATH, "PUT", JsonUtil.toJson(contactTokenList));
      ContactTokenDetailsList activeTokens     = JsonUtil.fromJson(response, ContactTokenDetailsList.class);

      return activeTokens.getContacts();
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new NonSuccessfulResponseCodeException("Unable to parse entity");
    }
  }

  public ContactTokenDetails getContactTokenDetails(String contactToken) throws IOException {
    try {
      String response = makeServiceRequest(String.format(DIRECTORY_VERIFY_PATH, contactToken), "GET", null);
      return JsonUtil.fromJson(response, ContactTokenDetails.class);
    } catch (NotFoundException nfe) {
      return null;
    }
  }

  private String getCredentials(String authPath) throws IOException {
    String              response = makeServiceRequest(authPath, "GET", null, NO_HEADERS);
    AuthCredentials     token    = JsonUtil.fromJson(response, AuthCredentials.class);
    return token.asBasic();
  }

  public String getContactDiscoveryAuthorization() throws IOException {
    return getCredentials(DIRECTORY_AUTH_PATH);
  }

  public String getKeyBackupServiceAuthorization() throws IOException {
    return getCredentials(KBS_AUTH_PATH);
  }

  public TokenResponse getKeyBackupServiceToken(String authorizationToken, String enclaveName)
    throws IOException
  {
    ResponseBody body = makeRequest(ClientSet.KeyBackup, authorizationToken, null, "/v1/token/" + enclaveName, "GET", null).body();

    if (body != null) {
      return JsonUtil.fromJson(body.string(), TokenResponse.class);
    } else {
      throw new NonSuccessfulResponseCodeException("Empty response!");
    }
  }

  public DiscoveryResponse getContactDiscoveryRegisteredUsers(String authorizationToken, DiscoveryRequest request, List<String> cookies, String mrenclave)
      throws IOException
  {
    ResponseBody body = makeRequest(ClientSet.ContactDiscovery, authorizationToken, cookies, "/v1/discovery/" + mrenclave, "PUT", JsonUtil.toJson(request)).body();

    if (body != null) {
      return JsonUtil.fromJson(body.string(), DiscoveryResponse.class);
    } else {
      throw new NonSuccessfulResponseCodeException("Empty response!");
    }
  }

  public KeyBackupResponse putKbsData(String authorizationToken, KeyBackupRequest request, List<String> cookies, String mrenclave)
      throws IOException
  {
    ResponseBody body = makeRequest(ClientSet.KeyBackup, authorizationToken, cookies, "/v1/backup/" + mrenclave, "PUT", JsonUtil.toJson(request)).body();

    if (body != null) {
      return JsonUtil.fromJson(body.string(), KeyBackupResponse.class);
    } else {
      throw new NonSuccessfulResponseCodeException("Empty response!");
    }
  }

  public void reportContactDiscoveryServiceMatch() throws IOException {
    makeServiceRequest(String.format(DIRECTORY_FEEDBACK_PATH, "ok"), "PUT", "");
  }

  public void reportContactDiscoveryServiceMismatch() throws IOException {
    makeServiceRequest(String.format(DIRECTORY_FEEDBACK_PATH, "mismatch"), "PUT", "");
  }

  public void reportContactDiscoveryServiceAttestationError(String reason) throws IOException {
    ContactDiscoveryFailureReason failureReason = new ContactDiscoveryFailureReason(reason);
    makeServiceRequest(String.format(DIRECTORY_FEEDBACK_PATH, "attestation-error"), "PUT", JsonUtil.toJson(failureReason));
  }

  public void reportContactDiscoveryServiceUnexpectedError(String reason) throws IOException {
    ContactDiscoveryFailureReason failureReason = new ContactDiscoveryFailureReason(reason);
    makeServiceRequest(String.format(DIRECTORY_FEEDBACK_PATH, "unexpected-error"), "PUT", JsonUtil.toJson(failureReason));
  }

  public TurnServerInfo getTurnServerInfo() throws IOException {
    String response = makeServiceRequest(TURN_SERVER_INFO, "GET", null);
    return JsonUtil.fromJson(response, TurnServerInfo.class);
  }

  public String getStorageAuth() throws IOException {
    String              response     = makeServiceRequest("/v1/storage/auth", "GET", null);
    StorageAuthResponse authResponse = JsonUtil.fromJson(response, StorageAuthResponse.class);

    return Credentials.basic(authResponse.getUsername(), authResponse.getPassword());
  }

  public StorageManifest getStorageManifest(String authToken) throws IOException {
    Response response = makeStorageRequest(authToken, "/v1/storage/manifest", "GET", null);

    if (response.body() == null) {
      throw new IOException("Missing body!");
    }

    return StorageManifest.parseFrom(response.body().bytes());
  }

  public StorageManifest getStorageManifestIfDifferentVersion(String authToken, long version) throws IOException {
    Response response = makeStorageRequest(authToken, "/v1/storage/manifest/version/" + version, "GET", null);

    if (response.body() == null) {
      throw new IOException("Missing body!");
    }

    return StorageManifest.parseFrom(response.body().bytes());
  }

  public StorageItems readStorageItems(String authToken, ReadOperation operation) throws IOException {
    Response response = makeStorageRequest(authToken, "/v1/storage/read", "PUT", operation.toByteArray());

    if (response.body() == null) {
      throw new IOException("Missing body!");
    }

    return StorageItems.parseFrom(response.body().bytes());
  }

  public Optional<StorageManifest> writeStorageContacts(String authToken, WriteOperation writeOperation) throws IOException {
    try {
      makeStorageRequest(authToken, "/v1/storage", "PUT", writeOperation.toByteArray());
      return Optional.absent();
    } catch (ContactManifestMismatchException e) {
      return Optional.of(StorageManifest.parseFrom(e.getResponseBody()));
    }
  }

  public RemoteConfigResponse getRemoteConfig() throws IOException {
    String response = makeServiceRequest("/v1/config", "GET", null);
    return JsonUtil.fromJson(response, RemoteConfigResponse.class);
  }

  public void setSoTimeoutMillis(long soTimeoutMillis) {
    this.soTimeoutMillis = soTimeoutMillis;
  }

  public void cancelInFlightRequests() {
    synchronized (connections) {
      Log.w(TAG, "Canceling: " + connections.size());
      for (Call connection : connections) {
        Log.w(TAG, "Canceling: " + connection);
        connection.cancel();
      }
    }
  }

  public AttachmentUploadAttributes getAttachmentUploadAttributes() throws NonSuccessfulResponseCodeException, PushNetworkException {
    String response = makeServiceRequest(ATTACHMENT_PATH, "GET", null);
    try {
      return JsonUtil.fromJson(response, AttachmentUploadAttributes.class);
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new NonSuccessfulResponseCodeException("Unable to parse entity");
    }
  }

  public Pair<Long, byte[]> uploadAttachment(PushAttachmentData attachment, AttachmentUploadAttributes uploadAttributes)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    long   id     = Long.parseLong(uploadAttributes.getAttachmentId());
    byte[] digest = uploadToCdn(ATTACHMENT_UPLOAD_PATH, uploadAttributes.getAcl(), uploadAttributes.getKey(),
                                uploadAttributes.getPolicy(), uploadAttributes.getAlgorithm(),
                                uploadAttributes.getCredential(), uploadAttributes.getDate(),
                                uploadAttributes.getSignature(), attachment.getData(),
                                "application/octet-stream", attachment.getDataSize(),
                                attachment.getOutputStreamFactory(), attachment.getListener(),
                                attachment.getCancelationSignal());

    return new Pair<>(id, digest);
  }

  private void downloadFromCdn(File destination, String path, int maxSizeBytes, ProgressListener listener)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    try (FileOutputStream outputStream = new FileOutputStream(destination, true)) {
      downloadFromCdn(outputStream, destination.length(), path, maxSizeBytes, listener);
    } catch (IOException e) {
      throw new PushNetworkException(e);
    }
  }

  private void downloadFromCdn(OutputStream outputStream, long offset, String path, int maxSizeBytes, ProgressListener listener)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    ConnectionHolder connectionHolder = getRandom(cdnClients, random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    Request.Builder request = new Request.Builder().url(connectionHolder.getUrl() + "/" + path).get();

    if (connectionHolder.getHostHeader().isPresent()) {
      request.addHeader("Host", connectionHolder.getHostHeader().get());
    }

    if (offset > 0) {
      Log.i(TAG, "Starting download from CDN with offset " + offset);
      request.addHeader("Range", "bytes=" + offset + "-");
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    Response     response = null;
    ResponseBody body     = null;

    try {
      response = call.execute();

      if (response.isSuccessful()) {
        body = response.body();

        if (body == null)                        throw new PushNetworkException("No response body!");
        if (body.contentLength() > maxSizeBytes) throw new PushNetworkException("Response exceeds max size!");

        InputStream  in     = body.byteStream();
        byte[]       buffer = new byte[32768];

        int  read      = 0;
        long totalRead = offset;

        while ((read = in.read(buffer, 0, buffer.length)) != -1) {
          outputStream.write(buffer, 0, read);
          if ((totalRead += read) > maxSizeBytes) throw new PushNetworkException("Response exceeded max size!");

          if (listener != null) {
            listener.onAttachmentProgress(body.contentLength() + offset, totalRead);
          }
        }

        return;
      }
    } catch (IOException e) {
      if (body != null) {
        body.close();
      }
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }

    throw new NonSuccessfulResponseCodeException("Response: " + response);
  }

  private byte[] uploadToCdn(String path, String acl, String key, String policy, String algorithm,
                             String credential, String date, String signature,
                             InputStream data, String contentType, long length,
                             OutputStreamFactory outputStreamFactory, ProgressListener progressListener,
                             CancelationSignal cancelationSignal)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    ConnectionHolder connectionHolder = getRandom(cdnClients, random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    DigestingRequestBody file = new DigestingRequestBody(data, outputStreamFactory, contentType, length, progressListener, cancelationSignal);

    RequestBody requestBody = new MultipartBody.Builder()
                                               .setType(MultipartBody.FORM)
                                               .addFormDataPart("acl", acl)
                                               .addFormDataPart("key", key)
                                               .addFormDataPart("policy", policy)
                                               .addFormDataPart("Content-Type", contentType)
                                               .addFormDataPart("x-amz-algorithm", algorithm)
                                               .addFormDataPart("x-amz-credential", credential)
                                               .addFormDataPart("x-amz-date", date)
                                               .addFormDataPart("x-amz-signature", signature)
                                               .addFormDataPart("file", "file", file)
                                               .build();

    Request.Builder request = new Request.Builder()
                                         .url(connectionHolder.getUrl() + "/" + path)
                                         .post(requestBody);

    if (connectionHolder.getHostHeader().isPresent()) {
      request.addHeader("Host", connectionHolder.getHostHeader().get());
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    try {
      Response response;

      try {
        response = call.execute();
      } catch (IOException e) {
        throw new PushNetworkException(e);
      }

      if (response.isSuccessful()) return file.getTransmittedDigest();
      else                         throw new NonSuccessfulResponseCodeException("Response: " + response);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }
  }

  private String makeServiceRequest(String urlFragment, String method, String body)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    return makeServiceRequest(urlFragment, method, body, NO_HEADERS, NO_HANDLER, Optional.<UnidentifiedAccess>absent());
  }

  private String makeServiceRequest(String urlFragment, String method, String body, Map<String, String> headers)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    return makeServiceRequest(urlFragment, method, body, headers, NO_HANDLER, Optional.<UnidentifiedAccess>absent());
  }

  private String makeServiceRequest(String urlFragment, String method, String body, Map<String, String> headers, ResponseCodeHandler responseCodeHandler)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    return makeServiceRequest(urlFragment, method, body, headers, responseCodeHandler, Optional.<UnidentifiedAccess>absent());
  }

  private String makeServiceRequest(String urlFragment, String method, String body, Map<String, String> headers, Optional<UnidentifiedAccess> unidentifiedAccessKey)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    return makeServiceRequest(urlFragment, method, body, headers, NO_HANDLER, unidentifiedAccessKey);
  }

  private String makeServiceRequest(String urlFragment, String method, String body, Map<String, String> headers, ResponseCodeHandler responseCodeHandler, Optional<UnidentifiedAccess> unidentifiedAccessKey)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    Response response = getServiceConnection(urlFragment, method, body, headers, unidentifiedAccessKey);

    int    responseCode;
    String responseMessage;
    String responseBody;

    try {
      responseCode    = response.code();
      responseMessage = response.message();
      responseBody    = response.body().string();
    } catch (IOException ioe) {
      throw new PushNetworkException(ioe);
    }

    responseCodeHandler.handle(responseCode);

    switch (responseCode) {
      case 413:
        throw new RateLimitException("Rate limit exceeded: " + responseCode);
      case 401:
      case 403:
        throw new AuthorizationFailedException("Authorization failed!");
      case 404:
        throw new NotFoundException("Not found");
      case 409:
        MismatchedDevices mismatchedDevices;

        try {
          mismatchedDevices = JsonUtil.fromJson(responseBody, MismatchedDevices.class);
        } catch (JsonProcessingException e) {
          Log.w(TAG, e);
          throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " + responseMessage);
        } catch (IOException e) {
          throw new PushNetworkException(e);
        }

        throw new MismatchedDevicesException(mismatchedDevices);
      case 410:
        StaleDevices staleDevices;

        try {
          staleDevices = JsonUtil.fromJson(responseBody, StaleDevices.class);
        } catch (JsonProcessingException e) {
          throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " + responseMessage);
        } catch (IOException e) {
          throw new PushNetworkException(e);
        }

        throw new StaleDevicesException(staleDevices);
      case 411:
        DeviceLimit deviceLimit;

        try {
          deviceLimit = JsonUtil.fromJson(responseBody, DeviceLimit.class);
        } catch (JsonProcessingException e) {
          throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " + responseMessage);
        } catch (IOException e) {
          throw new PushNetworkException(e);
        }

        throw new DeviceLimitExceededException(deviceLimit);
      case 417:
        throw new ExpectationFailedException();
      case 423:
        RegistrationLockFailure accountLockFailure;

        try {
          accountLockFailure = JsonUtil.fromJson(responseBody, RegistrationLockFailure.class);
        } catch (JsonProcessingException e) {
          Log.w(TAG, e);
          throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " + responseMessage);
        } catch (IOException e) {
          throw new PushNetworkException(e);
        }

        AuthCredentials credentials             = accountLockFailure.backupCredentials;
        String          basicStorageCredentials = credentials != null ? credentials.asBasic() : null;

        throw new LockedException(accountLockFailure.length,
                                  accountLockFailure.timeRemaining,
                                  basicStorageCredentials);
    }

    if (responseCode != 200 && responseCode != 204) {
      throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " + responseMessage);
    }

    return responseBody;
  }

  private Response getServiceConnection(String urlFragment, String method, String body, Map<String, String> headers, Optional<UnidentifiedAccess> unidentifiedAccess)
      throws PushNetworkException
  {
    try {
      ServiceConnectionHolder connectionHolder = (ServiceConnectionHolder) getRandom(serviceClients, random);
      OkHttpClient            baseClient       = unidentifiedAccess.isPresent() ? connectionHolder.getUnidentifiedClient() : connectionHolder.getClient();
      OkHttpClient            okHttpClient     = baseClient.newBuilder()
                                                           .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                           .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                           .build();

      Log.d(TAG, "Push service URL: " + connectionHolder.getUrl());
      Log.d(TAG, "Opening URL: " + String.format("%s%s", connectionHolder.getUrl(), urlFragment));

      Request.Builder request = new Request.Builder();
      request.url(String.format("%s%s", connectionHolder.getUrl(), urlFragment));

      if (body != null) {
        request.method(method, RequestBody.create(MediaType.parse("application/json"), body));
      } else {
        request.method(method, null);
      }

      for (Map.Entry<String, String> header : headers.entrySet()) {
        request.addHeader(header.getKey(), header.getValue());
      }

      if (!headers.containsKey("Authorization")) {
        if (unidentifiedAccess.isPresent()) {
          request.addHeader("Unidentified-Access-Key", Base64.encodeBytes(unidentifiedAccess.get().getUnidentifiedAccessKey()));
        } else if (credentialsProvider.getPassword() != null) {
          request.addHeader("Authorization", getAuthorizationHeader(credentialsProvider));
        }
      }

      if (signalAgent != null) {
        request.addHeader("X-Signal-Agent", signalAgent);
      }

      if (connectionHolder.getHostHeader().isPresent()) {
        request.addHeader("Host", connectionHolder.getHostHeader().get());
      }

      Call call = okHttpClient.newCall(request.build());

      synchronized (connections) {
        connections.add(call);
      }

      try {
        return call.execute();
      } finally {
        synchronized (connections) {
          connections.remove(call);
        }
      }
    } catch (IOException e) {
      throw new PushNetworkException(e);
    }
  }

  private ConnectionHolder[] clientsFor(ClientSet clientSet) {
    switch (clientSet) {
      case ContactDiscovery:
        return contactDiscoveryClients;
      case KeyBackup:
        return keyBackupServiceClients;
      default:
        throw new AssertionError("Unknown attestation purpose");
    }
  }

  Response makeRequest(ClientSet clientSet, String authorization, List<String> cookies, String path, String method, String body)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    ConnectionHolder connectionHolder = getRandom(clientsFor(clientSet), random);

    return makeRequest(connectionHolder, authorization, cookies, path, method, body);
  }

  private Response makeRequest(ConnectionHolder connectionHolder, String authorization, List<String> cookies, String path, String method, String body)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    OkHttpClient okHttpClient = connectionHolder.getClient()
                                                .newBuilder()
                                                .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                .build();

    Request.Builder request = new Request.Builder().url(connectionHolder.getUrl() + path);

    if (body != null) {
      request.method(method, RequestBody.create(MediaType.parse("application/json"), body));
    } else {
      request.method(method, null);
    }

    if (connectionHolder.getHostHeader().isPresent()) {
      request.addHeader("Host", connectionHolder.getHostHeader().get());
    }

    if (authorization != null) {
      request.addHeader("Authorization", authorization);
    }

    if (cookies != null && !cookies.isEmpty()) {
      request.addHeader("Cookie", Util.join(cookies, "; "));
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    Response response;

    try {
      response = call.execute();

      if (response.isSuccessful()) {
        return response;
      }
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }

    switch (response.code()) {
      case 401:
      case 403:
        throw new AuthorizationFailedException("Authorization failed!");
      case 409:
        throw new RemoteAttestationResponseExpiredException("Remote attestation response expired");
      case 429:
        throw new RateLimitException("Rate limit exceeded: " + response.code());
    }

    throw new NonSuccessfulResponseCodeException("Response: " + response);
  }

  private Response makeStorageRequest(String authorization, String path, String method, byte[] body)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    ConnectionHolder connectionHolder = getRandom(storageClients, random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    Log.d(TAG, "Opening URL: " + String.format("%s%s", connectionHolder.getUrl(), path));

    Request.Builder request = new Request.Builder().url(connectionHolder.getUrl() + path);

    if (body != null) {
      request.method(method, RequestBody.create(MediaType.parse("application/x-protobuf"), body));
    } else {
      request.method(method, null);
    }

    if (connectionHolder.getHostHeader().isPresent()) {
      request.addHeader("Host", connectionHolder.getHostHeader().get());
    }

    if (authorization != null) {
      request.addHeader("Authorization", authorization);
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    Response response;

    try {
      response = call.execute();

      if (response.isSuccessful() && response.code() != 204) {
        return response;
      }
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }

    switch (response.code()) {
      case 204:
        throw new NoContentException("No content!");
      case 401:
      case 403:
        throw new AuthorizationFailedException("Authorization failed!");
      case 404:
        throw new NotFoundException("Not found");
      case 409:
        if (response.body() != null) {
          try {
            throw new ContactManifestMismatchException(response.body().bytes());
          } catch (IOException e) {
            throw new PushNetworkException(e);
          }
        }
      case 429:
        throw new RateLimitException("Rate limit exceeded: " + response.code());
    }

    throw new NonSuccessfulResponseCodeException("Response: " + response);
  }

  private ServiceConnectionHolder[] createServiceConnectionHolders(SignalUrl[] urls, List<Interceptor> interceptors) {
    List<ServiceConnectionHolder> serviceConnectionHolders = new LinkedList<>();

    for (SignalUrl url : urls) {
      serviceConnectionHolders.add(new ServiceConnectionHolder(createConnectionClient(url, interceptors),
                                                               createConnectionClient(url, interceptors),
                                                               url.getUrl(), url.getHostHeader()));
    }

    return serviceConnectionHolders.toArray(new ServiceConnectionHolder[0]);
  }

  private ConnectionHolder[] createConnectionHolders(SignalUrl[] urls, List<Interceptor> interceptors) {
    List<ConnectionHolder> connectionHolders = new LinkedList<>();

    for (SignalUrl url : urls) {
      connectionHolders.add(new ConnectionHolder(createConnectionClient(url, interceptors), url.getUrl(), url.getHostHeader()));
    }

    return connectionHolders.toArray(new ConnectionHolder[0]);
  }

  private OkHttpClient createConnectionClient(SignalUrl url, List<Interceptor> interceptors) {
    try {
      TrustManager[] trustManagers = BlacklistingTrustManager.createFor(url.getTrustStore());

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustManagers, null);

      OkHttpClient.Builder builder = new OkHttpClient.Builder()
                                                     .sslSocketFactory(new Tls12SocketFactory(context.getSocketFactory()), (X509TrustManager)trustManagers[0])
                                                     .connectionSpecs(url.getConnectionSpecs().or(Util.immutableList(ConnectionSpec.RESTRICTED_TLS)));

      for (Interceptor interceptor : interceptors) {
        builder.addInterceptor(interceptor);
      }

      return builder.build();
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new AssertionError(e);
    }
  }

  private String getAuthorizationHeader(CredentialsProvider credentialsProvider) {
    try {
      String identifier = credentialsProvider.getUuid() != null ? credentialsProvider.getUuid().toString() : credentialsProvider.getE164();
      return "Basic " + Base64.encodeBytes((identifier + ":" + credentialsProvider.getPassword()).getBytes("UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  private ConnectionHolder getRandom(ConnectionHolder[] connections, SecureRandom random) {
    return connections[random.nextInt(connections.length)];
  }

  private static class GcmRegistrationId {

    @JsonProperty
    private String gcmRegistrationId;

    @JsonProperty
    private boolean webSocketChannel;

    public GcmRegistrationId() {}

    public GcmRegistrationId(String gcmRegistrationId, boolean webSocketChannel) {
      this.gcmRegistrationId = gcmRegistrationId;
      this.webSocketChannel  = webSocketChannel;
    }
  }

  private static class RegistrationLock {
    @JsonProperty
    private String pin;

    public RegistrationLock() {}

    public RegistrationLock(String pin) {
      this.pin = pin;
    }
  }

  private static class RegistrationLockV2 {
    @JsonProperty
    private String registrationLock;

    public RegistrationLockV2() {}

    public RegistrationLockV2(String registrationLock) {
      this.registrationLock = registrationLock;
    }
  }

  private static class RegistrationLockFailure {
    @JsonProperty
    private int length;

    @JsonProperty
    private long timeRemaining;

    @JsonProperty
    private AuthCredentials backupCredentials;
  }

  private static class ConnectionHolder {

    private final OkHttpClient     client;
    private final String           url;
    private final Optional<String> hostHeader;

    private ConnectionHolder(OkHttpClient client, String url, Optional<String> hostHeader) {
      this.client     = client;
      this.url        = url;
      this.hostHeader = hostHeader;
    }

    OkHttpClient getClient() {
      return client;
    }

    public String getUrl() {
      return url;
    }

    Optional<String> getHostHeader() {
      return hostHeader;
    }
  }

  private static class ServiceConnectionHolder extends ConnectionHolder {

    private final OkHttpClient unidentifiedClient;

    private ServiceConnectionHolder(OkHttpClient identifiedClient, OkHttpClient unidentifiedClient, String url, Optional<String> hostHeader) {
      super(identifiedClient, url, hostHeader);
      this.unidentifiedClient = unidentifiedClient;
    }

    OkHttpClient getUnidentifiedClient() {
      return unidentifiedClient;
    }
  }

  private interface ResponseCodeHandler {
    void handle(int responseCode) throws NonSuccessfulResponseCodeException, PushNetworkException;
  }

  private static class EmptyResponseCodeHandler implements ResponseCodeHandler {
    @Override
    public void handle(int responseCode) { }
  }

  public enum ClientSet { ContactDiscovery, KeyBackup }
}
