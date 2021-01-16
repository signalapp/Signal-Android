/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

import org.signal.storageservice.protos.groups.AvatarUploadAttributes;
import org.signal.storageservice.protos.groups.Group;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.GroupChanges;
import org.signal.storageservice.protos.groups.GroupExternalCredential;
import org.signal.storageservice.protos.groups.GroupJoinInfo;
import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.signal.zkgroup.profiles.ProfileKeyCredentialRequest;
import org.signal.zkgroup.profiles.ProfileKeyCredentialRequestContext;
import org.signal.zkgroup.profiles.ProfileKeyCredentialResponse;
import org.signal.zkgroup.profiles.ProfileKeyVersion;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.account.AccountAttributes;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.groupsv2.CredentialResponse;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2AuthorizationString;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId;
import org.whispersystems.signalservice.api.messages.calls.CallingResponse;
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
import org.whispersystems.signalservice.api.push.exceptions.ConflictException;
import org.whispersystems.signalservice.api.push.exceptions.ContactManifestMismatchException;
import org.whispersystems.signalservice.api.push.exceptions.DeprecatedVersionException;
import org.whispersystems.signalservice.api.push.exceptions.ExpectationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;
import org.whispersystems.signalservice.api.push.exceptions.NoContentException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.RangeException;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;
import org.whispersystems.signalservice.api.push.exceptions.RemoteAttestationResponseExpiredException;
import org.whispersystems.signalservice.api.push.exceptions.ResumeLocationInvalidException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.push.exceptions.UsernameMalformedException;
import org.whispersystems.signalservice.api.push.exceptions.UsernameTakenException;
import org.whispersystems.signalservice.api.storage.StorageAuthResponse;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.Tls12SocketFactory;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.configuration.SignalUrl;
import org.whispersystems.signalservice.internal.contacts.entities.DiscoveryRequest;
import org.whispersystems.signalservice.internal.contacts.entities.DiscoveryResponse;
import org.whispersystems.signalservice.internal.contacts.entities.KeyBackupRequest;
import org.whispersystems.signalservice.internal.contacts.entities.KeyBackupResponse;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;
import org.whispersystems.signalservice.internal.push.exceptions.ForbiddenException;
import org.whispersystems.signalservice.internal.push.exceptions.GroupExistsException;
import org.whispersystems.signalservice.internal.push.exceptions.GroupNotFoundException;
import org.whispersystems.signalservice.internal.push.exceptions.GroupPatchNotAcceptedException;
import org.whispersystems.signalservice.internal.push.exceptions.MismatchedDevicesException;
import org.whispersystems.signalservice.internal.push.exceptions.NotInGroupException;
import org.whispersystems.signalservice.internal.push.exceptions.StaleDevicesException;
import org.whispersystems.signalservice.internal.push.http.CancelationSignal;
import org.whispersystems.signalservice.internal.push.http.DigestingRequestBody;
import org.whispersystems.signalservice.internal.push.http.NoCipherOutputStreamFactory;
import org.whispersystems.signalservice.internal.push.http.OutputStreamFactory;
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec;
import org.whispersystems.signalservice.internal.storage.protos.ReadOperation;
import org.whispersystems.signalservice.internal.storage.protos.StorageItems;
import org.whispersystems.signalservice.internal.storage.protos.StorageManifest;
import org.whispersystems.signalservice.internal.storage.protos.WriteOperation;
import org.whispersystems.signalservice.internal.util.BlacklistingTrustManager;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.internal.util.concurrent.FutureTransformers;
import org.whispersystems.signalservice.internal.util.concurrent.ListenableFuture;
import org.whispersystems.signalservice.internal.util.concurrent.SettableFuture;
import org.whispersystems.util.Base64;
import org.whispersystems.util.Base64UrlSafe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.Dns;
import okhttp3.HttpUrl;
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
  private static final String DELETE_ACCOUNT_PATH       = "/v1/accounts/me";

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
  private static final String ATTACHMENT_V2_PATH        = "/v2/attachments/form/upload";
  private static final String ATTACHMENT_V3_PATH        = "/v3/attachments/form/upload";

  private static final String PROFILE_PATH              = "/v1/profile/%s";
  private static final String PROFILE_USERNAME_PATH     = "/v1/profile/username/%s";

  private static final String SENDER_CERTIFICATE_PATH         = "/v1/certificate/delivery?includeUuid=true";
  private static final String SENDER_CERTIFICATE_NO_E164_PATH = "/v1/certificate/delivery?includeUuid=true&includeE164=false";

  private static final String KBS_AUTH_PATH                  = "/v1/backup/auth";

  private static final String ATTACHMENT_KEY_DOWNLOAD_PATH   = "attachments/%s";
  private static final String ATTACHMENT_ID_DOWNLOAD_PATH    = "attachments/%d";
  private static final String ATTACHMENT_UPLOAD_PATH         = "attachments/";
  private static final String AVATAR_UPLOAD_PATH             = "";

  private static final String STICKER_MANIFEST_PATH          = "stickers/%s/manifest.proto";
  private static final String STICKER_PATH                   = "stickers/%s/full/%d";

  private static final String GROUPSV2_CREDENTIAL       = "/v1/certificate/group/%d/%d";
  private static final String GROUPSV2_GROUP            = "/v1/groups/";
  private static final String GROUPSV2_GROUP_PASSWORD   = "/v1/groups/?inviteLinkPassword=%s";
  private static final String GROUPSV2_GROUP_CHANGES    = "/v1/groups/logs/%s";
  private static final String GROUPSV2_AVATAR_REQUEST   = "/v1/groups/avatar/form";
  private static final String GROUPSV2_GROUP_JOIN       = "/v1/groups/join/%s";
  private static final String GROUPSV2_TOKEN            = "/v1/groups/token";

  private static final String SERVER_DELIVERED_TIMESTAMP_HEADER = "X-Signal-Timestamp";

  private static final Map<String, String> NO_HEADERS = Collections.emptyMap();
  private static final ResponseCodeHandler NO_HANDLER = new EmptyResponseCodeHandler();

  private static final long CDN2_RESUMABLE_LINK_LIFETIME_MILLIS = TimeUnit.DAYS.toMillis(7);

  private static final int MAX_FOLLOW_UPS = 20;

  private       long      soTimeoutMillis = TimeUnit.SECONDS.toMillis(30);
  private final Set<Call> connections     = new HashSet<>();

  private final ServiceConnectionHolder[]        serviceClients;
  private final Map<Integer, ConnectionHolder[]> cdnClientsMap;
  private final ConnectionHolder[]               contactDiscoveryClients;
  private final ConnectionHolder[]               keyBackupServiceClients;
  private final ConnectionHolder[]               storageClients;

  private final CredentialsProvider              credentialsProvider;
  private final String                           signalAgent;
  private final SecureRandom                     random;
  private final ClientZkProfileOperations        clientZkProfileOperations;
  private final boolean                          automaticNetworkRetry;

  public PushServiceSocket(SignalServiceConfiguration configuration,
                           CredentialsProvider credentialsProvider,
                           String signalAgent,
                           ClientZkProfileOperations clientZkProfileOperations,
                           boolean automaticNetworkRetry)
  {
    this.credentialsProvider       = credentialsProvider;
    this.signalAgent               = signalAgent;
    this.automaticNetworkRetry     = automaticNetworkRetry;
    this.serviceClients            = createServiceConnectionHolders(configuration.getSignalServiceUrls(), configuration.getNetworkInterceptors(), configuration.getDns());
    this.cdnClientsMap             = createCdnClientsMap(configuration.getSignalCdnUrlMap(), configuration.getNetworkInterceptors(), configuration.getDns());
    this.contactDiscoveryClients   = createConnectionHolders(configuration.getSignalContactDiscoveryUrls(), configuration.getNetworkInterceptors(), configuration.getDns());
    this.keyBackupServiceClients   = createConnectionHolders(configuration.getSignalKeyBackupServiceUrls(), configuration.getNetworkInterceptors(), configuration.getDns());
    this.storageClients            = createConnectionHolders(configuration.getSignalStorageUrls(), configuration.getNetworkInterceptors(), configuration.getDns());
    this.random                    = new SecureRandom();
    this.clientZkProfileOperations = clientZkProfileOperations;
  }

  public void requestSmsVerificationCode(boolean androidSmsRetriever, Optional<String> captchaToken, Optional<String> challenge) throws IOException {
    String path = String.format(CREATE_ACCOUNT_SMS_PATH, credentialsProvider.getE164(), androidSmsRetriever ? "android-2020-01" : "android");

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

  public VerifyAccountResponse verifyAccountCode(String verificationCode, String signalingKey, int registrationId, boolean fetchesMessages,
                                                 String pin, String registrationLock,
                                                 byte[] unidentifiedAccessKey, boolean unrestrictedUnidentifiedAccess,
                                                 AccountAttributes.Capabilities capabilities,
                                                 boolean discoverableByPhoneNumber)
      throws IOException
  {
    AccountAttributes signalingKeyEntity = new AccountAttributes(signalingKey, registrationId, fetchesMessages, pin, registrationLock, unidentifiedAccessKey, unrestrictedUnidentifiedAccess, capabilities, discoverableByPhoneNumber);
    String            requestBody        = JsonUtil.toJson(signalingKeyEntity);
    String            responseBody       = makeServiceRequest(String.format(VERIFY_ACCOUNT_CODE_PATH, verificationCode), "PUT", requestBody);

    return JsonUtil.fromJson(responseBody, VerifyAccountResponse.class);
  }

  public void setAccountAttributes(String signalingKey, int registrationId, boolean fetchesMessages,
                                   String pin, String registrationLock,
                                   byte[] unidentifiedAccessKey, boolean unrestrictedUnidentifiedAccess,
                                   AccountAttributes.Capabilities capabilities,
                                   boolean discoverableByPhoneNumber)
      throws IOException
  {
    if (registrationLock != null && pin != null) {
      throw new AssertionError("Pin should be null if registrationLock is set.");
    }

    AccountAttributes accountAttributes = new AccountAttributes(signalingKey, registrationId, fetchesMessages, pin, registrationLock,
                                                                unidentifiedAccessKey, unrestrictedUnidentifiedAccess, capabilities,
                                                                discoverableByPhoneNumber);
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
  public void removeRegistrationLockV1() throws IOException {
    makeServiceRequest(PIN_PATH, "DELETE", null);
  }

  public void setRegistrationLockV2(String registrationLock) throws IOException {
    RegistrationLockV2 accountLock = new RegistrationLockV2(registrationLock);
    makeServiceRequest(REGISTRATION_LOCK_PATH, "PUT", JsonUtil.toJson(accountLock));
  }

  public void disableRegistrationLockV2() throws IOException {
    makeServiceRequest(REGISTRATION_LOCK_PATH, "DELETE", null);
  }

  public byte[] getSenderCertificate() throws IOException {
    String responseText = makeServiceRequest(SENDER_CERTIFICATE_PATH, "GET", null);
    return JsonUtil.fromJson(responseText, SenderCertificate.class).getCertificate();
  }

  public byte[] getUuidOnlySenderCertificate() throws IOException {
    String responseText = makeServiceRequest(SENDER_CERTIFICATE_NO_E164_PATH, "GET", null);
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

  public Future<SendMessageResponse> submitMessage(OutgoingPushMessageList bundle, Optional<UnidentifiedAccess> unidentifiedAccess) {
    ListenableFuture<String> response = submitServiceRequest(String.format(MESSAGE_PATH, bundle.getDestination()), "PUT", JsonUtil.toJson(bundle), NO_HEADERS, unidentifiedAccess);

    return FutureTransformers.map(response, body -> {
      return body == null ? new SendMessageResponse(false)
          : JsonUtil.fromJson(body, SendMessageResponse.class);
    });
  }

  public SignalServiceMessagesResult getMessages() throws IOException {
    Response response = makeServiceRequest(String.format(MESSAGE_PATH, ""), "GET", (RequestBody) null, NO_HEADERS, NO_HANDLER, Optional.absent());
    validateServiceResponse(response);

    List<SignalServiceEnvelopeEntity> envelopes = readBodyJson(response.body(), SignalServiceEnvelopeEntityList.class).getMessages();

    long serverDeliveredTimestamp = 0;
    try {
      String stringValue = response.header(SERVER_DELIVERED_TIMESTAMP_HEADER);
      stringValue = stringValue != null ? stringValue : "0";

      serverDeliveredTimestamp = Long.parseLong(stringValue);
    } catch (NumberFormatException e) {
      Log.w(TAG, e);
    }

    return new SignalServiceMessagesResult(envelopes, serverDeliveredTimestamp);
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

  public void retrieveAttachment(int cdnNumber, SignalServiceAttachmentRemoteId cdnPath, File destination, long maxSizeBytes, ProgressListener listener)
      throws IOException, MissingConfigurationException
  {
    final String path;
    if (cdnPath.getV2().isPresent()) {
      path = String.format(Locale.US, ATTACHMENT_ID_DOWNLOAD_PATH, cdnPath.getV2().get());
    } else {
      path = String.format(Locale.US, ATTACHMENT_KEY_DOWNLOAD_PATH, cdnPath.getV3().get());
    }
    downloadFromCdn(destination, cdnNumber, path, maxSizeBytes, listener);
  }

  public byte[] retrieveSticker(byte[] packId, int stickerId)
      throws NonSuccessfulResponseCodeException, PushNetworkException {
    String                hexPackId = Hex.toStringCondensed(packId);
    ByteArrayOutputStream output    = new ByteArrayOutputStream();

    try {
      downloadFromCdn(output, 0, 0, String.format(Locale.US, STICKER_PATH, hexPackId, stickerId), 1024 * 1024, null);
    } catch (MissingConfigurationException e) {
      throw new AssertionError(e);
    }

    return output.toByteArray();
  }

  public byte[] retrieveStickerManifest(byte[] packId)
      throws NonSuccessfulResponseCodeException, PushNetworkException {
    String                hexPackId = Hex.toStringCondensed(packId);
    ByteArrayOutputStream output    = new ByteArrayOutputStream();

    try {
      downloadFromCdn(output, 0, 0, String.format(STICKER_MANIFEST_PATH, hexPackId), 1024 * 1024, null);
    } catch (MissingConfigurationException e) {
      throw new AssertionError(e);
    }

    return output.toByteArray();
  }

  public ListenableFuture<SignalServiceProfile> retrieveProfile(SignalServiceAddress target, Optional<UnidentifiedAccess> unidentifiedAccess) {
    ListenableFuture<String> response = submitServiceRequest(String.format(PROFILE_PATH, target.getIdentifier()), "GET", null, NO_HEADERS, unidentifiedAccess);

    return FutureTransformers.map(response, body -> {
      try {
        return JsonUtil.fromJson(body, SignalServiceProfile.class);
      } catch (IOException e) {
        Log.w(TAG, e);
        throw new NonSuccessfulResponseCodeException("Unable to parse entity");
      }
    });
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

  public ListenableFuture<ProfileAndCredential> retrieveVersionedProfileAndCredential(UUID target, ProfileKey profileKey, Optional<UnidentifiedAccess> unidentifiedAccess) {
    ProfileKeyVersion                  profileKeyIdentifier = profileKey.getProfileKeyVersion(target);
    ProfileKeyCredentialRequestContext requestContext       = clientZkProfileOperations.createProfileKeyCredentialRequestContext(random, target, profileKey);
    ProfileKeyCredentialRequest        request              = requestContext.getRequest();

    String version           = profileKeyIdentifier.serialize();
    String credentialRequest = Hex.toStringCondensed(request.serialize());
    String subPath           = String.format("%s/%s/%s", target, version, credentialRequest);

    ListenableFuture<String> response = submitServiceRequest(String.format(PROFILE_PATH, subPath), "GET", null, NO_HEADERS, unidentifiedAccess);

    return FutureTransformers.map(response, body -> formatProfileAndCredentialBody(requestContext, body));
  }

  private ProfileAndCredential formatProfileAndCredentialBody(ProfileKeyCredentialRequestContext requestContext, String body)
      throws NonSuccessfulResponseCodeException
  {
    try {
      SignalServiceProfile signalServiceProfile = JsonUtil.fromJson(body, SignalServiceProfile.class);

      try {
        ProfileKeyCredential profileKeyCredential = signalServiceProfile.getProfileKeyCredentialResponse() != null
                                                      ? clientZkProfileOperations.receiveProfileKeyCredential(requestContext, signalServiceProfile.getProfileKeyCredentialResponse())
                                                      : null;
        return new ProfileAndCredential(signalServiceProfile, SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL, Optional.fromNullable(profileKeyCredential));
      } catch (VerificationFailedException e) {
        Log.w(TAG, "Failed to verify credential.", e);
        return new ProfileAndCredential(signalServiceProfile, SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL, Optional.absent());
      }
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new NonSuccessfulResponseCodeException("Unable to parse entity");
    }
  }

  public ListenableFuture<SignalServiceProfile> retrieveVersionedProfile(UUID target, ProfileKey profileKey, Optional<UnidentifiedAccess> unidentifiedAccess) {
    ProfileKeyVersion profileKeyIdentifier = profileKey.getProfileKeyVersion(target);

    String                   version  = profileKeyIdentifier.serialize();
    String                   subPath  = String.format("%s/%s", target, version);
    ListenableFuture<String> response = submitServiceRequest(String.format(PROFILE_PATH, subPath), "GET", null, NO_HEADERS, unidentifiedAccess);

    return FutureTransformers.map(response, body -> {
      try {
        return JsonUtil.fromJson(body, SignalServiceProfile.class);
      } catch (IOException e) {
        Log.w(TAG, e);
        throw new NonSuccessfulResponseCodeException("Unable to parse entity");
      }
    });
  }

  public void retrieveProfileAvatar(String path, File destination, long maxSizeBytes)
      throws IOException
  {
    try {
      downloadFromCdn(destination, 0, path, maxSizeBytes, null);
    } catch (MissingConfigurationException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * @return The avatar URL path, if one was written.
   */
  public Optional<String> writeProfile(SignalServiceProfileWrite signalServiceProfileWrite, ProfileAvatarData profileAvatar)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
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

      uploadToCdn0(AVATAR_UPLOAD_PATH, formAttributes.getAcl(), formAttributes.getKey(),
                  formAttributes.getPolicy(), formAttributes.getAlgorithm(),
                  formAttributes.getCredential(), formAttributes.getDate(),
                  formAttributes.getSignature(), profileAvatar.getData(),
                  profileAvatar.getContentType(), profileAvatar.getDataLength(),
                  profileAvatar.getOutputStreamFactory(), null, null);

       return Optional.of(formAttributes.getKey());
    }

    return Optional.absent();
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

  public void deleteAccount() throws IOException {
    makeServiceRequest(DELETE_ACCOUNT_PATH, "DELETE", null);
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
    ResponseBody response = makeStorageRequest(authToken, "/v1/storage/manifest", "GET", null);

    if (response == null) {
      throw new IOException("Missing body!");
    }

    return StorageManifest.parseFrom(readBodyBytes(response));
  }

  public StorageManifest getStorageManifestIfDifferentVersion(String authToken, long version) throws IOException {
    ResponseBody response = makeStorageRequest(authToken, "/v1/storage/manifest/version/" + version, "GET", null);

    if (response == null) {
      throw new IOException("Missing body!");
    }

    return StorageManifest.parseFrom(readBodyBytes(response));
  }

  public StorageItems readStorageItems(String authToken, ReadOperation operation) throws IOException {
    ResponseBody response = makeStorageRequest(authToken, "/v1/storage/read", "PUT", protobufRequestBody(operation));

    if (response == null) {
      throw new IOException("Missing body!");
    }

    return StorageItems.parseFrom(readBodyBytes(response));
  }

  public Optional<StorageManifest> writeStorageContacts(String authToken, WriteOperation writeOperation) throws IOException {
    try {
      makeStorageRequest(authToken, "/v1/storage", "PUT", protobufRequestBody(writeOperation));
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

  public AttachmentV2UploadAttributes getAttachmentV2UploadAttributes() throws NonSuccessfulResponseCodeException, PushNetworkException {
    String response = makeServiceRequest(ATTACHMENT_V2_PATH, "GET", null);
    try {
      return JsonUtil.fromJson(response, AttachmentV2UploadAttributes.class);
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new NonSuccessfulResponseCodeException("Unable to parse entity");
    }
  }

  public AttachmentV3UploadAttributes getAttachmentV3UploadAttributes() throws NonSuccessfulResponseCodeException, PushNetworkException {
    String response = makeServiceRequest(ATTACHMENT_V3_PATH, "GET", null);
    try {
      return JsonUtil.fromJson(response, AttachmentV3UploadAttributes.class);
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new NonSuccessfulResponseCodeException("Unable to parse entity");
    }
  }

  public byte[] uploadGroupV2Avatar(byte[] avatarCipherText, AvatarUploadAttributes uploadAttributes)
      throws IOException
  {
    return uploadToCdn0(AVATAR_UPLOAD_PATH, uploadAttributes.getAcl(), uploadAttributes.getKey(),
                       uploadAttributes.getPolicy(), uploadAttributes.getAlgorithm(),
                       uploadAttributes.getCredential(), uploadAttributes.getDate(),
                       uploadAttributes.getSignature(),
                       new ByteArrayInputStream(avatarCipherText),
                       "application/octet-stream", avatarCipherText.length,
                       new NoCipherOutputStreamFactory(),
                       null, null);
  }

  public Pair<Long, byte[]> uploadAttachment(PushAttachmentData attachment, AttachmentV2UploadAttributes uploadAttributes)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    long   id     = Long.parseLong(uploadAttributes.getAttachmentId());
    byte[] digest = uploadToCdn0(ATTACHMENT_UPLOAD_PATH, uploadAttributes.getAcl(), uploadAttributes.getKey(),
                                uploadAttributes.getPolicy(), uploadAttributes.getAlgorithm(),
                                uploadAttributes.getCredential(), uploadAttributes.getDate(),
                                uploadAttributes.getSignature(), attachment.getData(),
                                "application/octet-stream", attachment.getDataSize(),
                                attachment.getOutputStreamFactory(), attachment.getListener(),
                                attachment.getCancelationSignal());

    return new Pair<>(id, digest);
  }

  public ResumableUploadSpec getResumableUploadSpec(AttachmentV3UploadAttributes uploadAttributes) throws IOException {
    return new ResumableUploadSpec(Util.getSecretBytes(64),
                                   Util.getSecretBytes(16),
                                   uploadAttributes.getKey(),
                                   uploadAttributes.getCdn(),
                                   getResumableUploadUrl(uploadAttributes.getSignedUploadLocation(), uploadAttributes.getHeaders()),
                                   System.currentTimeMillis() + CDN2_RESUMABLE_LINK_LIFETIME_MILLIS);
  }

  public byte[] uploadAttachment(PushAttachmentData attachment) throws IOException {

    if (attachment.getResumableUploadSpec() == null || attachment.getResumableUploadSpec().getExpirationTimestamp() < System.currentTimeMillis()) {
      throw new ResumeLocationInvalidException();
    }

    return uploadToCdn2(attachment.getResumableUploadSpec().getResumeLocation(),
                        attachment.getData(),
                        "application/octet-stream",
                        attachment.getDataSize(),
                        attachment.getOutputStreamFactory(),
                        attachment.getListener(),
                        attachment.getCancelationSignal());
  }

  private void downloadFromCdn(File destination, int cdnNumber, String path, long maxSizeBytes, ProgressListener listener)
      throws IOException, MissingConfigurationException
  {
    try (FileOutputStream outputStream = new FileOutputStream(destination, true)) {
      downloadFromCdn(outputStream, destination.length(), cdnNumber, path, maxSizeBytes, listener);
    }
  }

  private void downloadFromCdn(OutputStream outputStream, long offset, int cdnNumber, String path, long maxSizeBytes, ProgressListener listener)
      throws PushNetworkException, NonSuccessfulResponseCodeException, MissingConfigurationException {
    ConnectionHolder[] cdnNumberClients = cdnClientsMap.get(cdnNumber);
    if (cdnNumberClients == null) {
      throw new MissingConfigurationException("Attempted to download from unsupported CDN number: " + cdnNumber + ", Our configuration supports: " + cdnClientsMap.keySet());
    }
    ConnectionHolder   connectionHolder = getRandom(cdnNumberClients, random);
    OkHttpClient       okHttpClient     = connectionHolder.getClient()
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
      } else if (response.code() == 416) {
        throw new RangeException(offset);
      }
    } catch (NonSuccessfulResponseCodeException | PushNetworkException e) {
      throw e;
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      if (body != null) {
        body.close();
      }
      synchronized (connections) {
        connections.remove(call);
      }
    }

    throw new NonSuccessfulResponseCodeException("Response: " + response);
  }

  private byte[] uploadToCdn0(String path, String acl, String key, String policy, String algorithm,
                              String credential, String date, String signature,
                              InputStream data, String contentType, long length,
                              OutputStreamFactory outputStreamFactory, ProgressListener progressListener,
                              CancelationSignal cancelationSignal)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    ConnectionHolder connectionHolder = getRandom(cdnClientsMap.get(0), random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    DigestingRequestBody file = new DigestingRequestBody(data, outputStreamFactory, contentType, length, progressListener, cancelationSignal, 0);

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

  private String getResumableUploadUrl(String signedUrl, Map<String, String> headers) throws IOException {
    ConnectionHolder connectionHolder = getRandom(cdnClientsMap.get(2), random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    Request.Builder request = new Request.Builder().url(buildConfiguredUrl(connectionHolder, signedUrl))
                                                   .post(RequestBody.create(null, ""));

    for (Map.Entry<String, String> header : headers.entrySet()) {
      if (!header.getKey().equalsIgnoreCase("host")) {
        request.header(header.getKey(), header.getValue());
      }
    }

    if (connectionHolder.getHostHeader().isPresent()) {
      request.header("host", connectionHolder.getHostHeader().get());
    }

    request.addHeader("Content-Length", "0");
    request.addHeader("Content-Type", "application/octet-stream");

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

      if (response.isSuccessful()) {
        return response.header("location");
      } else {
        throw new NonSuccessfulResponseCodeException("Response: " + response);
      }
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }
  }

  private byte[] uploadToCdn2(String resumableUrl, InputStream data, String contentType, long length, OutputStreamFactory outputStreamFactory, ProgressListener progressListener, CancelationSignal cancelationSignal) throws IOException {
    ConnectionHolder connectionHolder = getRandom(cdnClientsMap.get(2), random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    ResumeInfo           resumeInfo = getResumeInfo(resumableUrl, length);
    DigestingRequestBody file       = new DigestingRequestBody(data, outputStreamFactory, contentType, length, progressListener, cancelationSignal, resumeInfo.contentStart);

    if (resumeInfo.contentStart == length) {
      Log.w(TAG, "Resume start point == content length");
      try (NowhereBufferedSink buffer = new NowhereBufferedSink()) {
        file.writeTo(buffer);
      }
      return file.getTransmittedDigest();
    }

    Request.Builder request = new Request.Builder().url(buildConfiguredUrl(connectionHolder, resumableUrl))
                                                   .put(file)
                                                   .addHeader("Content-Range", resumeInfo.contentRange);

    if (connectionHolder.getHostHeader().isPresent()) {
      request.header("host", connectionHolder.getHostHeader().get());
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

  private ResumeInfo getResumeInfo(String resumableUrl, long contentLength) throws IOException {
    ConnectionHolder connectionHolder = getRandom(cdnClientsMap.get(2), random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    final long   offset;
    final String contentRange;

    Request.Builder request = new Request.Builder().url(buildConfiguredUrl(connectionHolder, resumableUrl))
                                                   .put(RequestBody.create(null, ""))
                                                   .addHeader("Content-Range", String.format(Locale.US, "bytes */%d", contentLength));

    if (connectionHolder.getHostHeader().isPresent()) {
      request.header("host", connectionHolder.getHostHeader().get());
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

      if (response.isSuccessful()) {
        offset       = contentLength;
        contentRange = null;
      } else if (response.code() == 308) {
        String rangeCompleted = response.header("Range");

        if (rangeCompleted == null) {
          offset = 0;
        } else {
          offset = Long.parseLong(rangeCompleted.split("-")[1]) + 1;
        }

        contentRange = String.format(Locale.US, "bytes %d-%d/%d", offset, contentLength - 1, contentLength);
      } else if (response.code() == 404) {
        throw new ResumeLocationInvalidException();
      } else {
        throw new NonSuccessfulResponseCodeException("Response: " + response);
      }
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }

    return new ResumeInfo(contentRange, offset);
  }

  private static HttpUrl buildConfiguredUrl(ConnectionHolder connectionHolder, String url) throws IOException {
    final HttpUrl endpointUrl = HttpUrl.get(connectionHolder.url);
    final HttpUrl resumableHttpUrl;
    try {
      resumableHttpUrl = HttpUrl.get(url);
    } catch (IllegalArgumentException e) {
      throw new IOException("Malformed URL!", e);
    }

    return new HttpUrl.Builder().scheme(endpointUrl.scheme())
                                .host(endpointUrl.host())
                                .port(endpointUrl.port())
                                .encodedPath(endpointUrl.encodedPath())
                                .addEncodedPathSegments(resumableHttpUrl.encodedPath().substring(1))
                                .encodedQuery(resumableHttpUrl.encodedQuery())
                                .encodedFragment(resumableHttpUrl.encodedFragment())
                                .build();
  }

  private String makeServiceRequest(String urlFragment, String method, String jsonBody)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    return makeServiceRequest(urlFragment, method, jsonBody, NO_HEADERS, NO_HANDLER, Optional.<UnidentifiedAccess>absent());
  }

  private String makeServiceRequest(String urlFragment, String method, String jsonBody, Map<String, String> headers)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    return makeServiceRequest(urlFragment, method, jsonBody, headers, NO_HANDLER, Optional.<UnidentifiedAccess>absent());
  }

  private String makeServiceRequest(String urlFragment, String method, String jsonBody, Map<String, String> headers, ResponseCodeHandler responseCodeHandler)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    return makeServiceRequest(urlFragment, method, jsonBody, headers, responseCodeHandler, Optional.<UnidentifiedAccess>absent());
  }

  private String makeServiceRequest(String urlFragment, String method, String jsonBody, Map<String, String> headers, Optional<UnidentifiedAccess> unidentifiedAccessKey)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    return makeServiceRequest(urlFragment, method, jsonBody, headers, NO_HANDLER, unidentifiedAccessKey);
  }

  private String makeServiceRequest(String urlFragment, String method, String jsonBody, Map<String, String> headers, ResponseCodeHandler responseCodeHandler, Optional<UnidentifiedAccess> unidentifiedAccessKey)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    ResponseBody responseBody = makeServiceBodyRequest(urlFragment, method, jsonRequestBody(jsonBody), headers, responseCodeHandler, unidentifiedAccessKey);
    try {
      return responseBody.string();
    } catch (IOException e) {
      throw new PushNetworkException(e);
    }
  }

  private static RequestBody jsonRequestBody(String jsonBody) {
    return jsonBody != null
        ? RequestBody.create(MediaType.parse("application/json"), jsonBody)
        : null;
  }

  private static RequestBody protobufRequestBody(MessageLite protobufBody) {
    return protobufBody != null
        ? RequestBody.create(MediaType.parse("application/x-protobuf"), protobufBody.toByteArray())
        : null;
  }


  private ListenableFuture<String> submitServiceRequest(String urlFragment, String method, String jsonBody, Map<String, String> headers, Optional<UnidentifiedAccess> unidentifiedAccessKey) {
    OkHttpClient okHttpClient = buildOkHttpClient(unidentifiedAccessKey.isPresent());
    Call         call         = okHttpClient.newCall(buildServiceRequest(urlFragment, method, jsonRequestBody(jsonBody), headers, unidentifiedAccessKey));

    synchronized (connections) {
      connections.add(call);
    }

    SettableFuture<String> bodyFuture = new SettableFuture<>();

    call.enqueue(new Callback() {
      @Override
      public void onResponse(Call call, Response response) {
        try (ResponseBody body = validateServiceResponse(response).body()) {
          bodyFuture.set(readBodyString(body));
        } catch (IOException e) {
          bodyFuture.setException(e);
        }
      }

      @Override
      public void onFailure(Call call, IOException e) {
        bodyFuture.setException(e);
      }
    });

    return bodyFuture;
  }

  private ResponseBody makeServiceBodyRequest(String urlFragment,
                                              String method,
                                              RequestBody body,
                                              Map<String, String> headers,
                                              ResponseCodeHandler responseCodeHandler,
                                              Optional<UnidentifiedAccess> unidentifiedAccessKey)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    return makeServiceRequest(urlFragment, method, body, headers, responseCodeHandler, unidentifiedAccessKey).body();
  }

  private Response makeServiceRequest(String urlFragment,
                                      String method,
                                      RequestBody body,
                                      Map<String, String> headers,
                                      ResponseCodeHandler responseCodeHandler,
                                      Optional<UnidentifiedAccess> unidentifiedAccessKey)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    Response response = getServiceConnection(urlFragment, method, body, headers, unidentifiedAccessKey);

    responseCodeHandler.handle(response.code());

    return validateServiceResponse(response);
  }

  private Response validateServiceResponse(Response response) throws NonSuccessfulResponseCodeException, PushNetworkException {
    int    responseCode    = response.code();
    String responseMessage = response.message();

    switch (responseCode) {
      case 413:
        throw new RateLimitException("Rate limit exceeded: " + responseCode);
      case 401:
      case 403:
        throw new AuthorizationFailedException("Authorization failed!");
      case 404:
        throw new NotFoundException("Not found");
      case 409:
        MismatchedDevices mismatchedDevices = readResponseJson(response, MismatchedDevices.class);

        throw new MismatchedDevicesException(mismatchedDevices);
      case 410:
        StaleDevices staleDevices = readResponseJson(response, StaleDevices.class);

        throw new StaleDevicesException(staleDevices);
      case 411:
        DeviceLimit deviceLimit = readResponseJson(response, DeviceLimit.class);

        throw new DeviceLimitExceededException(deviceLimit);
      case 417:
        throw new ExpectationFailedException();
      case 423:
        RegistrationLockFailure accountLockFailure      = readResponseJson(response, RegistrationLockFailure.class);
        AuthCredentials         credentials             = accountLockFailure.backupCredentials;
        String                  basicStorageCredentials = credentials != null ? credentials.asBasic() : null;

        throw new LockedException(accountLockFailure.length,
                                  accountLockFailure.timeRemaining,
                                  basicStorageCredentials);
      case 499:
        throw new DeprecatedVersionException();

      case 508:
        throw new ServerRejectedException();
    }

    if (responseCode != 200 && responseCode != 204) {
      throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " + responseMessage);
    }

    return response;
  }

  private Response getServiceConnection(String urlFragment, String method, RequestBody body, Map<String, String> headers, Optional<UnidentifiedAccess> unidentifiedAccess)
      throws PushNetworkException
  {
    try {
      OkHttpClient okHttpClient = buildOkHttpClient(unidentifiedAccess.isPresent());
      Call         call         = okHttpClient.newCall(buildServiceRequest(urlFragment, method, body, headers, unidentifiedAccess));

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

  private OkHttpClient buildOkHttpClient(boolean unidentified) {
    ServiceConnectionHolder connectionHolder = (ServiceConnectionHolder) getRandom(serviceClients, random);
    OkHttpClient            baseClient       = unidentified ? connectionHolder.getUnidentifiedClient() : connectionHolder.getClient();

    return baseClient.newBuilder()
                     .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                     .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                     .retryOnConnectionFailure(automaticNetworkRetry)
                     .build();
  }

  private Request buildServiceRequest(String urlFragment, String method, RequestBody body, Map<String, String> headers, Optional<UnidentifiedAccess> unidentifiedAccess) {
    ServiceConnectionHolder connectionHolder = (ServiceConnectionHolder) getRandom(serviceClients, random);

//      Log.d(TAG, "Push service URL: " + connectionHolder.getUrl());
//      Log.d(TAG, "Opening URL: " + String.format("%s%s", connectionHolder.getUrl(), urlFragment));

    Request.Builder request = new Request.Builder();
    request.url(String.format("%s%s", connectionHolder.getUrl(), urlFragment));
    request.method(method, body);

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

    return request.build();
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

  private ResponseBody makeStorageRequest(String authorization, String path, String method, RequestBody body)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    return makeStorageRequest(authorization, path, method, body, NO_HANDLER);
  }

  private ResponseBody makeStorageRequest(String authorization, String path, String method, RequestBody body, ResponseCodeHandler responseCodeHandler)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    return makeStorageRequestResponse(authorization, path, method, body, responseCodeHandler).body();
  }

  private Response makeStorageRequestResponse(String authorization, String path, String method, RequestBody body, ResponseCodeHandler responseCodeHandler)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    ConnectionHolder connectionHolder = getRandom(storageClients, random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

//    Log.d(TAG, "Opening URL: " + connectionHolder.getUrl());

    Request.Builder request = new Request.Builder().url(connectionHolder.getUrl() + path);
    request.method(method, body);

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

    responseCodeHandler.handle(response.code());

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
          throw new ContactManifestMismatchException(readBodyBytes(response.body()));
        } else {
          throw new ConflictException();
        }
      case 429:
        throw new RateLimitException("Rate limit exceeded: " + response.code());
      case 499:
        throw new DeprecatedVersionException();
    }

    throw new NonSuccessfulResponseCodeException("Response: " + response);
  }

  public CallingResponse makeCallingRequest(long requestId, String url, String httpMethod, List<Pair<String, String>> headers, byte[] body) {
    ConnectionHolder connectionHolder = getRandom(serviceClients, random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .followRedirects(false)
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    RequestBody     requestBody = body != null ? RequestBody.create(null, body) : null;
    Request.Builder builder     = new Request.Builder()
                                             .url(url)
                                             .method(httpMethod, requestBody);

    if (headers != null) {
      for (Pair<String, String> header : headers) {
        builder.addHeader(header.first(), header.second());
      }
    }

    Request request = builder.build();

    for (int i = 0; i < MAX_FOLLOW_UPS; i++) {
      try (Response response = okHttpClient.newCall(request).execute()) {
        int responseStatus = response.code();

        if (responseStatus != 307) {
          return new CallingResponse.Success(requestId,
                                             responseStatus,
                                             response.body() != null ? response.body().bytes() : new byte[0]);
        }

        String  location = response.header("Location");
        HttpUrl newUrl   = location != null ? request.url().resolve(location) : null;

        if (newUrl != null) {
          request = request.newBuilder().url(newUrl).build();
        } else {
          return new CallingResponse.Error(requestId, new IOException("Received redirect without a valid Location header"));
        }
      } catch (IOException e) {
        Log.w(TAG, "Exception during ringrtc http call.", e);
        return new CallingResponse.Error(requestId, e);
      }
    }

    Log.w(TAG, "Calling request max redirects exceeded");
    return new CallingResponse.Error(requestId, new IOException("Redirect limit exceeded"));
  }

  private ServiceConnectionHolder[] createServiceConnectionHolders(SignalUrl[] urls,
                                                                   List<Interceptor> interceptors,
                                                                   Optional<Dns> dns)
  {
    List<ServiceConnectionHolder> serviceConnectionHolders = new LinkedList<>();

    for (SignalUrl url : urls) {
      serviceConnectionHolders.add(new ServiceConnectionHolder(createConnectionClient(url, interceptors, dns),
                                                               createConnectionClient(url, interceptors, dns),
                                                               url.getUrl(), url.getHostHeader()));
    }

    return serviceConnectionHolders.toArray(new ServiceConnectionHolder[0]);
  }

  private static Map<Integer, ConnectionHolder[]> createCdnClientsMap(final Map<Integer, SignalCdnUrl[]> signalCdnUrlMap,
                                                                      final List<Interceptor> interceptors,
                                                                      final Optional<Dns> dns) {
    validateConfiguration(signalCdnUrlMap);
    final Map<Integer, ConnectionHolder[]> result = new HashMap<>();
    for (Map.Entry<Integer, SignalCdnUrl[]> entry : signalCdnUrlMap.entrySet()) {
      result.put(entry.getKey(),
                 createConnectionHolders(entry.getValue(), interceptors, dns));
    }
    return Collections.unmodifiableMap(result);
  }

  private static void validateConfiguration(Map<Integer, SignalCdnUrl[]> signalCdnUrlMap) {
    if (!signalCdnUrlMap.containsKey(0) || !signalCdnUrlMap.containsKey(2)) {
      throw new AssertionError("Configuration used to create PushServiceSocket must support CDN 0 and CDN 2");
    }
  }

  private static ConnectionHolder[] createConnectionHolders(SignalUrl[] urls, List<Interceptor> interceptors, Optional<Dns> dns) {
    List<ConnectionHolder> connectionHolders = new LinkedList<>();

    for (SignalUrl url : urls) {
      connectionHolders.add(new ConnectionHolder(createConnectionClient(url, interceptors, dns), url.getUrl(), url.getHostHeader()));
    }

    return connectionHolders.toArray(new ConnectionHolder[0]);
  }

  private static OkHttpClient createConnectionClient(SignalUrl url, List<Interceptor> interceptors, Optional<Dns> dns) {
    try {
      TrustManager[] trustManagers = BlacklistingTrustManager.createFor(url.getTrustStore());

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustManagers, null);

      OkHttpClient.Builder builder = new OkHttpClient.Builder()
                                                     .sslSocketFactory(new Tls12SocketFactory(context.getSocketFactory()), (X509TrustManager)trustManagers[0])
                                                     .connectionSpecs(url.getConnectionSpecs().or(Util.immutableList(ConnectionSpec.RESTRICTED_TLS)))
                                                     .dns(dns.or(Dns.SYSTEM));

      builder.sslSocketFactory(new Tls12SocketFactory(context.getSocketFactory()), (X509TrustManager)trustManagers[0])
             .connectionSpecs(url.getConnectionSpecs().or(Util.immutableList(ConnectionSpec.RESTRICTED_TLS)))
             .build();

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

  public ProfileKeyCredential parseResponse(UUID uuid, ProfileKey profileKey, ProfileKeyCredentialResponse profileKeyCredentialResponse) throws VerificationFailedException {
    ProfileKeyCredentialRequestContext profileKeyCredentialRequestContext = clientZkProfileOperations.createProfileKeyCredentialRequestContext(random, uuid, profileKey);

    return clientZkProfileOperations.receiveProfileKeyCredential(profileKeyCredentialRequestContext, profileKeyCredentialResponse);
  }

  /**
   * Converts {@link IOException} on body byte reading to {@link PushNetworkException}.
   */
  private static byte[] readBodyBytes(ResponseBody response) throws PushNetworkException {
    if (response == null) {
      throw new PushNetworkException("No body!");
    }
    try {
      return response.bytes();
    } catch (IOException e) {
      throw new PushNetworkException(e);
    }
  }

  /**
   * Converts {@link IOException} on body reading to {@link PushNetworkException}.
   */
  private static String readBodyString(ResponseBody body) throws PushNetworkException {
    if (body == null) {
      throw new PushNetworkException("No body!");
    }

    try {
      return body.string();
    } catch (IOException e) {
      throw new PushNetworkException(e);
    }
  }

  /**
   * Converts {@link IOException} on body reading to {@link PushNetworkException}.
   * {@link IOException} during json parsing is converted to a {@link NonSuccessfulResponseCodeException}
   */
  private static <T> T readBodyJson(ResponseBody body, Class<T> clazz)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    String json = readBodyString(body);
    try {
      return JsonUtil.fromJson(json, clazz);
    } catch (JsonProcessingException e) {
      Log.w(TAG, e);
      throw new NonSuccessfulResponseCodeException("Unable to parse entity");
    } catch (IOException e) {
      throw new PushNetworkException(e);
    }
  }

  /**
   * Converts {@link IOException} on body reading to {@link PushNetworkException}.
   * {@link IOException} during json parsing is converted to a {@link NonSuccessfulResponseCodeException} with response code detail.
   */
  private static <T> T readResponseJson(Response response, Class<T> clazz)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    try {
      return readBodyJson(response.body(), clazz);
    } catch (NonSuccessfulResponseCodeException e) {
      throw new NonSuccessfulResponseCodeException("Bad response: " + response.code() + " " + response.message());
    }
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

  public CredentialResponse retrieveGroupsV2Credentials(int today)
      throws IOException
  {
    int    todayPlus7 = today + 7;
    String response   = makeServiceRequest(String.format(Locale.US, GROUPSV2_CREDENTIAL, today, todayPlus7),
                                           "GET",
                                           null,
                                           NO_HEADERS,
                                           Optional.absent());

    return JsonUtil.fromJson(response, CredentialResponse.class);
  }

  private static final ResponseCodeHandler GROUPS_V2_PUT_RESPONSE_HANDLER   = responseCode -> {
    if (responseCode == 409) throw new GroupExistsException();
  };;
  private static final ResponseCodeHandler GROUPS_V2_GET_LOGS_HANDLER       = NO_HANDLER;
  private static final ResponseCodeHandler GROUPS_V2_GET_CURRENT_HANDLER    = responseCode -> {
    switch (responseCode) {
      case 403: throw new NotInGroupException();
      case 404: throw new GroupNotFoundException();
    }
  };
  private static final ResponseCodeHandler GROUPS_V2_PATCH_RESPONSE_HANDLER = responseCode -> {
    if (responseCode == 400) throw new GroupPatchNotAcceptedException();
  };
  private static final ResponseCodeHandler GROUPS_V2_GET_JOIN_INFO_HANDLER  = responseCode -> {
    if (responseCode == 403) throw new ForbiddenException();
  };

  public void putNewGroupsV2Group(Group group, GroupsV2AuthorizationString authorization)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
      makeStorageRequest(authorization.toString(),
                         GROUPSV2_GROUP,
                         "PUT",
                         protobufRequestBody(group),
                         GROUPS_V2_PUT_RESPONSE_HANDLER);
  }

  public Group getGroupsV2Group(GroupsV2AuthorizationString authorization)
      throws NonSuccessfulResponseCodeException, PushNetworkException, InvalidProtocolBufferException
  {
      ResponseBody response = makeStorageRequest(authorization.toString(),
                                                 GROUPSV2_GROUP,
                                                 "GET",
                                                 null,
                                                 GROUPS_V2_GET_CURRENT_HANDLER);

    return Group.parseFrom(readBodyBytes(response));
  }

  public AvatarUploadAttributes getGroupsV2AvatarUploadForm(String authorization)
      throws NonSuccessfulResponseCodeException, PushNetworkException, InvalidProtocolBufferException
  {
      ResponseBody response = makeStorageRequest(authorization,
                                                 GROUPSV2_AVATAR_REQUEST,
                                                 "GET",
                                                 null,
                                                 NO_HANDLER);

    return AvatarUploadAttributes.parseFrom(readBodyBytes(response));
  }

  public GroupChange patchGroupsV2Group(GroupChange.Actions groupChange, String authorization, Optional<byte[]> groupLinkPassword)
      throws NonSuccessfulResponseCodeException, PushNetworkException, InvalidProtocolBufferException
  {
    String path;

    if (groupLinkPassword.isPresent()) {
      path = String.format(GROUPSV2_GROUP_PASSWORD, Base64UrlSafe.encodeBytesWithoutPadding(groupLinkPassword.get()));
    } else {
      path = GROUPSV2_GROUP;
    }

    ResponseBody response = makeStorageRequest(authorization,
                                               path,
                                               "PATCH",
                                               protobufRequestBody(groupChange),
                                               GROUPS_V2_PATCH_RESPONSE_HANDLER);

    return GroupChange.parseFrom(readBodyBytes(response));
  }

  public GroupHistory getGroupsV2GroupHistory(int fromVersion, GroupsV2AuthorizationString authorization)
    throws NonSuccessfulResponseCodeException, PushNetworkException, InvalidProtocolBufferException
  {
    Response response = makeStorageRequestResponse(authorization.toString(),
                                                   String.format(Locale.US, GROUPSV2_GROUP_CHANGES, fromVersion),
                                                   "GET",
                                                   null,
                                                   GROUPS_V2_GET_LOGS_HANDLER);

    GroupChanges groupChanges = GroupChanges.parseFrom(readBodyBytes(response.body()));

    if (response.code() == 206) {
      String                 contentRangeHeader = response.header("Content-Range");
      Optional<ContentRange> contentRange       = ContentRange.parse(contentRangeHeader);

      if (contentRange.isPresent()) {
        Log.i(TAG, "Additional logs for group: " + contentRangeHeader);
        return new GroupHistory(groupChanges, contentRange);
      } else {
        Log.w(TAG, "Unable to parse Content-Range header: " + contentRangeHeader);
        throw new NonSuccessfulResponseCodeException("Unable to parse content range header on 206");
      }
    }

    return new GroupHistory(groupChanges, Optional.absent());
  }

  public GroupJoinInfo getGroupJoinInfo(Optional<byte[]> groupLinkPassword, GroupsV2AuthorizationString authorization)
      throws NonSuccessfulResponseCodeException, PushNetworkException, InvalidProtocolBufferException
  {
    String       passwordParam = groupLinkPassword.transform(Base64UrlSafe::encodeBytesWithoutPadding).or("");
    ResponseBody response      = makeStorageRequest(authorization.toString(),
                                                    String.format(GROUPSV2_GROUP_JOIN, passwordParam),
                                                    "GET",
                                                    null,
                                                    GROUPS_V2_GET_JOIN_INFO_HANDLER);

    return GroupJoinInfo.parseFrom(readBodyBytes(response));
  }

  public GroupExternalCredential getGroupExternalCredential(GroupsV2AuthorizationString authorization)
    throws NonSuccessfulResponseCodeException, PushNetworkException, InvalidProtocolBufferException
  {
    ResponseBody response = makeStorageRequest(authorization.toString(),
                                               GROUPSV2_TOKEN,
                                               "GET",
                                               null,
                                               NO_HANDLER);

    return GroupExternalCredential.parseFrom(readBodyBytes(response));
  }

  public static final class GroupHistory {
    private final GroupChanges           groupChanges;
    private final Optional<ContentRange> contentRange;

    public GroupHistory(GroupChanges groupChanges, Optional<ContentRange> contentRange) {
      this.groupChanges = groupChanges;
      this.contentRange = contentRange;
    }

    public GroupChanges getGroupChanges() {
      return groupChanges;
    }

    public boolean hasMore() {
      return contentRange.isPresent();
    }

    /**
     * Valid iff {@link #hasMore()}.
     */
    public int getNextPageStartGroupRevision() {
      return contentRange.get().getRangeEnd() + 1;
    }
  }

  private final class ResumeInfo {
    private final String contentRange;
    private final long   contentStart;

    private ResumeInfo(String contentRange, long offset) {
      this.contentRange = contentRange;
      this.contentStart = offset;
    }
  }
}
