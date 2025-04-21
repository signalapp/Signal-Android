/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.squareup.wire.Message;

import org.signal.core.util.Base64;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.protocol.util.Pair;
import org.signal.storageservice.protos.groups.AvatarUploadAttributes;
import org.signal.storageservice.protos.groups.Group;
import org.signal.storageservice.protos.groups.GroupChange;
import org.signal.storageservice.protos.groups.GroupChangeResponse;
import org.signal.storageservice.protos.groups.GroupChanges;
import org.signal.storageservice.protos.groups.GroupExternalCredential;
import org.signal.storageservice.protos.groups.GroupJoinInfo;
import org.signal.storageservice.protos.groups.GroupResponse;
import org.signal.storageservice.protos.groups.Member;
import org.whispersystems.signalservice.api.NetworkResult;
import org.whispersystems.signalservice.api.account.AccountAttributes;
import org.whispersystems.signalservice.api.account.PreKeyCollection;
import org.whispersystems.signalservice.api.crypto.SealedSenderAccess;
import org.whispersystems.signalservice.api.groupsv2.GroupsV2AuthorizationString;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentRemoteId;
import org.whispersystems.signalservice.api.messages.calls.CallingResponse;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;
import org.whispersystems.signalservice.api.push.exceptions.AlreadyVerifiedException;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.ChallengeRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.ConflictException;
import org.whispersystems.signalservice.api.push.exceptions.ContactManifestMismatchException;
import org.whispersystems.signalservice.api.push.exceptions.DeprecatedVersionException;
import org.whispersystems.signalservice.api.push.exceptions.ExpectationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.ExternalServiceFailureException;
import org.whispersystems.signalservice.api.push.exceptions.HttpConflictException;
import org.whispersystems.signalservice.api.push.exceptions.IncorrectRegistrationRecoveryPasswordException;
import org.whispersystems.signalservice.api.push.exceptions.InvalidTransportModeException;
import org.whispersystems.signalservice.api.push.exceptions.MalformedRequestException;
import org.whispersystems.signalservice.api.push.exceptions.MalformedResponseException;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;
import org.whispersystems.signalservice.api.push.exceptions.MustRequestNewCodeException;
import org.whispersystems.signalservice.api.push.exceptions.NoContentException;
import org.whispersystems.signalservice.api.push.exceptions.NoSuchSessionException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResumableUploadResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.RangeException;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;
import org.whispersystems.signalservice.api.push.exceptions.RequestVerificationCodeRateLimitException;
import org.whispersystems.signalservice.api.push.exceptions.ResumeLocationInvalidException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.signalservice.api.push.exceptions.SubmitVerificationCodeRateLimitException;
import org.whispersystems.signalservice.api.push.exceptions.TokenNotAcceptedException;
import org.whispersystems.signalservice.api.registration.RestoreMethodBody;
import org.whispersystems.signalservice.api.svr.Svr3Credentials;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.Tls12SocketFactory;
import org.whispersystems.signalservice.api.util.TlsProxySocketFactory;
import org.whispersystems.signalservice.internal.configuration.SignalCdnUrl;
import org.whispersystems.signalservice.internal.configuration.SignalProxy;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.configuration.SignalUrl;
import org.whispersystems.signalservice.internal.crypto.AttachmentDigest;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
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
import okhttp3.internal.http2.StreamResetException;

/**
 * @author Moxie Marlinspike
 */
public class PushServiceSocket {

  private static final String TAG = PushServiceSocket.class.getSimpleName();

  private static final String SET_RESTORE_METHOD_PATH   = "/v1/devices/restore_account/%s";

  private static final String ATTACHMENT_KEY_DOWNLOAD_PATH   = "attachments/%s";
  private static final String ATTACHMENT_ID_DOWNLOAD_PATH    = "attachments/%d";
  private static final String AVATAR_UPLOAD_PATH             = "";

  private static final String STICKER_MANIFEST_PATH          = "stickers/%s/manifest.proto";
  private static final String STICKER_PATH                   = "stickers/%s/full/%d";

  private static final String GROUPSV2_GROUP            = "/v2/groups/";
  private static final String GROUPSV2_GROUP_PASSWORD   = "/v2/groups/?inviteLinkPassword=%s";
  private static final String GROUPSV2_GROUP_CHANGES    = "/v2/groups/logs/%s?maxSupportedChangeEpoch=%d&includeFirstState=%s&includeLastState=false";
  private static final String GROUPSV2_AVATAR_REQUEST   = "/v2/groups/avatar/form";
  private static final String GROUPSV2_GROUP_JOIN       = "/v2/groups/join/%s";
  private static final String GROUPSV2_TOKEN            = "/v2/groups/token";
  private static final String GROUPSV2_JOINED_AT        = "/v2/groups/joined_at_version";

  private static final String VERIFICATION_SESSION_PATH = "/v1/verification/session";
  private static final String VERIFICATION_CODE_PATH    = "/v1/verification/session/%s/code";

  private static final String REGISTRATION_PATH    = "/v1/registration";

  private static final String BACKUP_AUTH_CHECK_V2 = "/v2/backup/auth/check";
  private static final String BACKUP_AUTH_CHECK_V3 = "/v3/backup/auth/check";

  private static final String ARCHIVE_MEDIA_DOWNLOAD_PATH = "backups/%s/%s";

  private static final Map<String, String> NO_HEADERS                         = Collections.emptyMap();
  private static final ResponseCodeHandler NO_HANDLER                         = new EmptyResponseCodeHandler();
  private static final ResponseCodeHandler UNOPINIONATED_HANDLER              = new UnopinionatedResponseCodeHandler();
  private static final ResponseCodeHandler UNOPINIONATED_BINARY_ERROR_HANDLER = new UnopinionatedBinaryErrorResponseCodeHandler();

  public static final long CDN2_RESUMABLE_LINK_LIFETIME_MILLIS = TimeUnit.DAYS.toMillis(7);

  private static final int MAX_FOLLOW_UPS = 20;

  private       long      soTimeoutMillis = TimeUnit.SECONDS.toMillis(30);
  private final Set<Call> connections     = new HashSet<>();

  private final ServiceConnectionHolder[]        serviceClients;
  private final Map<Integer, ConnectionHolder[]> cdnClientsMap;
  private final ConnectionHolder[]               storageClients;

  private final SignalServiceConfiguration       configuration;
  private final CredentialsProvider              credentialsProvider;
  private final String                           signalAgent;
  private final SecureRandom                     random;
  private final boolean                          automaticNetworkRetry;

  public PushServiceSocket(SignalServiceConfiguration configuration,
                           CredentialsProvider credentialsProvider,
                           String signalAgent,
                           boolean automaticNetworkRetry)
  {
    this.configuration             = configuration;
    this.credentialsProvider       = credentialsProvider;
    this.signalAgent               = signalAgent;
    this.automaticNetworkRetry     = automaticNetworkRetry;
    this.serviceClients            = createServiceConnectionHolders(configuration.getSignalServiceUrls(), configuration.getNetworkInterceptors(), configuration.getDns(), configuration.getSignalProxy());
    this.cdnClientsMap             = createCdnClientsMap(configuration.getSignalCdnUrlMap(), configuration.getNetworkInterceptors(), configuration.getDns(), configuration.getSignalProxy());
    this.storageClients            = createConnectionHolders(configuration.getSignalStorageUrls(), configuration.getNetworkInterceptors(), configuration.getDns(), configuration.getSignalProxy());
    this.random                    = new SecureRandom();
  }

  public SignalServiceConfiguration getConfiguration() {
    return configuration;
  }

  public CredentialsProvider getCredentialsProvider() {
    return credentialsProvider;
  }

  public RegistrationSessionMetadataResponse createVerificationSession(@Nullable String pushToken, @Nullable String mcc, @Nullable String mnc) throws IOException {
    final String jsonBody = JsonUtil.toJson(new VerificationSessionMetadataRequestBody(credentialsProvider.getE164(), pushToken, mcc, mnc));
    try (Response response = makeServiceRequest(VERIFICATION_SESSION_PATH, "POST", jsonRequestBody(jsonBody), NO_HEADERS, new RegistrationSessionResponseHandler(), SealedSenderAccess.NONE, false)) {
      return parseSessionMetadataResponse(response);
    }
  }

  public RegistrationSessionMetadataResponse getSessionStatus(String sessionId) throws IOException {
    String path = VERIFICATION_SESSION_PATH + "/" + sessionId;

    try (Response response = makeServiceRequest(path, "GET", jsonRequestBody(null), NO_HEADERS, new RegistrationSessionResponseHandler(), SealedSenderAccess.NONE, false)) {
      return parseSessionMetadataResponse(response);
    }
  }

  public RegistrationSessionMetadataResponse patchVerificationSession(String sessionId, @Nullable String pushToken, @Nullable String mcc, @Nullable String mnc, @Nullable String captchaToken, @Nullable String pushChallengeToken) throws IOException {
    String path = VERIFICATION_SESSION_PATH + "/" + sessionId;

    final UpdateVerificationSessionRequestBody requestBody = new UpdateVerificationSessionRequestBody(captchaToken, pushToken, pushChallengeToken, mcc, mnc);
    try (Response response = makeServiceRequest(path, "PATCH", jsonRequestBody(JsonUtil.toJson(requestBody)), NO_HEADERS, new PatchRegistrationSessionResponseHandler(), SealedSenderAccess.NONE, false)) {
      return parseSessionMetadataResponse(response);
    }
  }

  public RegistrationSessionMetadataResponse requestVerificationCode(String sessionId, Locale locale, boolean androidSmsRetriever, VerificationCodeTransport transport) throws IOException {
    String path = String.format(VERIFICATION_CODE_PATH, sessionId);
    Map<String, String> headers = locale != null ? Collections.singletonMap("Accept-Language", locale.getLanguage() + "-" + locale.getCountry()) : NO_HEADERS;
    Map<String, String> body    = new HashMap<>();

    switch (transport) {
      case SMS:
        body.put("transport", "sms");
        break;
      case VOICE:
        body.put("transport", "voice");
        break;
    }

    body.put("client", androidSmsRetriever ? "android-2021-03" : "android");

    try (Response response = makeServiceRequest(path, "POST", jsonRequestBody(JsonUtil.toJson(body)), headers, new RequestVerificationCodeResponseHandler(), SealedSenderAccess.NONE, false)) {
      return parseSessionMetadataResponse(response);
    }
  }

  public RegistrationSessionMetadataResponse submitVerificationCode(String sessionId, String verificationCode) throws IOException {
    String path = String.format(VERIFICATION_CODE_PATH, sessionId);
    Map<String, String> body =  new HashMap<>();
    body.put("code", verificationCode);
    try (Response response = makeServiceRequest(path, "PUT", jsonRequestBody(JsonUtil.toJson(body)), NO_HEADERS, new SubmitVerificationCodeResponseHandler(), SealedSenderAccess.NONE, false)) {
      return parseSessionMetadataResponse(response);
    }
  }

  public VerifyAccountResponse submitRegistrationRequest(@Nullable String sessionId, @Nullable String recoveryPassword, AccountAttributes attributes, PreKeyCollection aciPreKeys, PreKeyCollection pniPreKeys, @Nullable String fcmToken, boolean skipDeviceTransfer) throws IOException {
    String path = REGISTRATION_PATH;
    if (sessionId == null && recoveryPassword == null) {
      throw new IllegalArgumentException("Neither Session ID nor Recovery Password provided.");
    }

    if (sessionId != null && recoveryPassword != null) {
      throw new IllegalArgumentException("You must supply one and only one of either: Session ID, or Recovery Password.");
    }

    GcmRegistrationId gcmRegistrationId;
    if (attributes.getFetchesMessages()) {
      gcmRegistrationId = null;
    } else {
      gcmRegistrationId = new GcmRegistrationId(fcmToken, true);
    }

    RegistrationSessionRequestBody body;
    try {
      final SignedPreKeyEntity aciSignedPreKey = new SignedPreKeyEntity(Objects.requireNonNull(aciPreKeys.getSignedPreKey()).getId(),
                                                                        aciPreKeys.getSignedPreKey().getKeyPair().getPublicKey(),
                                                                        aciPreKeys.getSignedPreKey().getSignature());
      final SignedPreKeyEntity pniSignedPreKey = new SignedPreKeyEntity(Objects.requireNonNull(pniPreKeys.getSignedPreKey()).getId(),
                                                                        pniPreKeys.getSignedPreKey().getKeyPair().getPublicKey(),
                                                                        pniPreKeys.getSignedPreKey().getSignature());
      final KyberPreKeyEntity aciLastResortKyberPreKey = new KyberPreKeyEntity(Objects.requireNonNull(aciPreKeys.getLastResortKyberPreKey()).getId(),
                                                                               aciPreKeys.getLastResortKyberPreKey().getKeyPair().getPublicKey(),
                                                                               aciPreKeys.getLastResortKyberPreKey().getSignature());
      final KyberPreKeyEntity pniLastResortKyberPreKey = new KyberPreKeyEntity(Objects.requireNonNull(pniPreKeys.getLastResortKyberPreKey()).getId(),
                                                                               pniPreKeys.getLastResortKyberPreKey().getKeyPair().getPublicKey(),
                                                                               pniPreKeys.getLastResortKyberPreKey().getSignature());

      body = new RegistrationSessionRequestBody(sessionId,
                                                recoveryPassword,
                                                attributes,
                                                Base64.encodeWithoutPadding(aciPreKeys.getIdentityKey().serialize()),
                                                Base64.encodeWithoutPadding(pniPreKeys.getIdentityKey().serialize()),
                                                aciSignedPreKey,
                                                pniSignedPreKey,
                                                aciLastResortKyberPreKey,
                                                pniLastResortKyberPreKey,
                                                gcmRegistrationId,
                                                skipDeviceTransfer,
                                                true);
    } catch (InvalidKeyException e) {
      throw new AssertionError("unexpected invalid key", e);
    }

    String response = makeServiceRequest(path, "POST", JsonUtil.toJson(body), NO_HEADERS, new RegistrationSessionResponseHandler(), SealedSenderAccess.NONE);
    return JsonUtil.fromJson(response, VerifyAccountResponse.class);
  }

  public void setRestoreMethodChosen(@Nonnull String token, @Nonnull RestoreMethodBody request) throws IOException {
    String body = JsonUtil.toJson(request);
    makeServiceRequest(String.format(Locale.US, SET_RESTORE_METHOD_PATH, urlEncode(token)), "PUT", body, NO_HEADERS, UNOPINIONATED_HANDLER, SealedSenderAccess.NONE);
  }

  public void requestPushChallenge(String sessionId, String gcmRegistrationId) throws IOException {
    patchVerificationSession(sessionId, gcmRegistrationId, null, null, null, null);
  }

  public void retrieveBackup(int cdnNumber, Map<String, String> headers, String cdnPath, File destination, long maxSizeBytes, ProgressListener listener)
      throws MissingConfigurationException, IOException
  {
    downloadFromCdn(destination, cdnNumber, headers, cdnPath, maxSizeBytes, listener);
  }

  public void retrieveAttachment(int cdnNumber, Map<String, String> headers, SignalServiceAttachmentRemoteId remoteId, File destination, long maxSizeBytes, ProgressListener listener)
      throws IOException, MissingConfigurationException
  {
    final String path;
    if (remoteId instanceof SignalServiceAttachmentRemoteId.V2) {
      path = String.format(Locale.US, ATTACHMENT_ID_DOWNLOAD_PATH, ((SignalServiceAttachmentRemoteId.V2) remoteId).getCdnId());
    } else if (remoteId instanceof SignalServiceAttachmentRemoteId.V4) {
      String urlEncodedKey = urlEncode(((SignalServiceAttachmentRemoteId.V4) remoteId).getCdnKey());
      path = String.format(Locale.US, ATTACHMENT_KEY_DOWNLOAD_PATH, urlEncodedKey);
    } else if (remoteId instanceof SignalServiceAttachmentRemoteId.Backup) {
      //noinspection PatternVariableCanBeUsed
      SignalServiceAttachmentRemoteId.Backup backupCdnRemoteId = (SignalServiceAttachmentRemoteId.Backup) remoteId;
      path = String.format(Locale.US, ARCHIVE_MEDIA_DOWNLOAD_PATH, backupCdnRemoteId.getMediaCdnPath(), backupCdnRemoteId.getMediaId());
    } else {
      throw new IllegalArgumentException("Invalid cdnPath type: " + remoteId.getClass().getSimpleName());
    }
    downloadFromCdn(destination, cdnNumber, headers, path, maxSizeBytes, listener);
  }

  public byte[] retrieveSticker(byte[] packId, int stickerId)
      throws NonSuccessfulResponseCodeException, PushNetworkException {
    String                hexPackId = Hex.toStringCondensed(packId);
    ByteArrayOutputStream output    = new ByteArrayOutputStream();

    try {
      downloadFromCdn(output, 0, 0, Collections.emptyMap(), String.format(Locale.US, STICKER_PATH, hexPackId, stickerId), 1024 * 1024, null);
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
      downloadFromCdn(output, 0, 0, Collections.emptyMap(), String.format(STICKER_MANIFEST_PATH, hexPackId), 1024 * 1024, null);
    } catch (MissingConfigurationException e) {
      throw new AssertionError(e);
    }

    return output.toByteArray();
  }

  public void retrieveProfileAvatar(String path, File destination, long maxSizeBytes)
      throws IOException
  {
    try {
      downloadFromCdn(destination, 0, Collections.emptyMap(), path, maxSizeBytes, null);
    } catch (MissingConfigurationException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Only used to upload self profile avatar as part of writing the profile to the service.
   *
   * @return Profile avatar key
   */
  public NetworkResult<String> uploadProfileAvatar(ProfileAvatarUploadAttributes formAttributes, ProfileAvatarData profileAvatar) {
    return NetworkResult.fromFetch(() -> {
      uploadToCdn0(AVATAR_UPLOAD_PATH, formAttributes.getAcl(), formAttributes.getKey(),
                   formAttributes.getPolicy(), formAttributes.getAlgorithm(),
                   formAttributes.getCredential(), formAttributes.getDate(),
                   formAttributes.getSignature(), profileAvatar.getData(),
                   profileAvatar.getContentType(), profileAvatar.getDataLength(), false,
                   profileAvatar.getOutputStreamFactory(), null, null);

      return formAttributes.getKey();
    });
  }

  public BackupV2AuthCheckResponse checkSvr2AuthCredentials(@Nullable String number, @Nonnull List<String> passwords) throws IOException {
    String response = makeServiceRequest(BACKUP_AUTH_CHECK_V2, "POST", JsonUtil.toJson(new BackupAuthCheckRequest(number, passwords)), NO_HEADERS, UNOPINIONATED_HANDLER, SealedSenderAccess.NONE);
    return JsonUtil.fromJson(response, BackupV2AuthCheckResponse.class);
  }

  public BackupV3AuthCheckResponse checkSvr3AuthCredentials(@Nullable String number, @Nonnull List<String> passwords) throws IOException {
    String response = makeServiceRequest(BACKUP_AUTH_CHECK_V3, "POST", JsonUtil.toJson(new BackupAuthCheckRequest(number, passwords)), NO_HEADERS, UNOPINIONATED_HANDLER, SealedSenderAccess.NONE);
    return JsonUtil.fromJson(response, BackupV3AuthCheckResponse.class);
  }

  public StorageManifest getStorageManifest(String authToken) throws IOException {
    try (Response response = makeStorageRequest(authToken, "/v1/storage/manifest", "GET", null, NO_HANDLER)) {
      return StorageManifest.ADAPTER.decode(readBodyBytes(response));
    }
  }

  public StorageManifest getStorageManifestIfDifferentVersion(String authToken, long version) throws IOException {
    try (Response response = makeStorageRequest(authToken, "/v1/storage/manifest/version/" + version, "GET", null, NO_HANDLER)) {
      return StorageManifest.ADAPTER.decode(readBodyBytes(response));
    }
  }

  public StorageItems readStorageItems(String authToken, ReadOperation operation) throws IOException {
    try (Response response = makeStorageRequest(authToken, "/v1/storage/read", "PUT", protobufRequestBody(operation), NO_HANDLER)) {
      return StorageItems.ADAPTER.decode(readBodyBytes(response));
    }
  }

  public void writeStorageItems(String authToken, WriteOperation writeOperation) throws IOException {
    makeStorageRequest(authToken, "/v1/storage", "PUT", protobufRequestBody(writeOperation), UNOPINIONATED_BINARY_ERROR_HANDLER);
  }

  public void pingStorageService() throws IOException {
    try (Response response = makeStorageRequest(null, "/ping", "GET", null, NO_HANDLER)) {
      return;
    }
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

  public AttachmentDigest uploadGroupV2Avatar(byte[] avatarCipherText, AvatarUploadAttributes uploadAttributes)
      throws IOException
  {
    return uploadToCdn0(AVATAR_UPLOAD_PATH, uploadAttributes.acl, uploadAttributes.key,
                       uploadAttributes.policy, uploadAttributes.algorithm,
                       uploadAttributes.credential, uploadAttributes.date,
                       uploadAttributes.signature,
                       new ByteArrayInputStream(avatarCipherText),
                       "application/octet-stream", avatarCipherText.length, false,
                       new NoCipherOutputStreamFactory(),
                       null, null);
  }

  public ResumableUploadSpec getResumableUploadSpec(AttachmentUploadForm uploadForm) throws IOException {
    return new ResumableUploadSpec(Util.getSecretBytes(64),
                                   Util.getSecretBytes(16),
                                   uploadForm.key,
                                   uploadForm.cdn,
                                   getResumableUploadUrl(uploadForm),
                                   System.currentTimeMillis() + CDN2_RESUMABLE_LINK_LIFETIME_MILLIS,
                                   uploadForm.headers);
  }

  public AttachmentDigest uploadAttachment(PushAttachmentData attachment) throws IOException {

    if (attachment.getResumableUploadSpec().getExpirationTimestamp() < System.currentTimeMillis()) {
      throw new ResumeLocationInvalidException();
    }

    if (attachment.getResumableUploadSpec().getCdnNumber() == 2) {
      return uploadToCdn2(attachment.getResumableUploadSpec().getResumeLocation(),
                          attachment.getData(),
                          "application/octet-stream",
                          attachment.getDataSize(),
                          attachment.getIncremental(),
                          attachment.getOutputStreamFactory(),
                          attachment.getListener(),
                          attachment.getCancelationSignal());
    } else {
      return uploadToCdn3(attachment.getResumableUploadSpec().getResumeLocation(),
                          attachment.getData(),
                          "application/offset+octet-stream",
                          attachment.getDataSize(),
                          attachment.getIncremental(),
                          attachment.getOutputStreamFactory(),
                          attachment.getListener(),
                          attachment.getCancelationSignal(),
                          attachment.getResumableUploadSpec().getHeaders());
    }
  }

  private void downloadFromCdn(File destination, int cdnNumber, Map<String, String> headers, String path, long maxSizeBytes, ProgressListener listener)
      throws IOException, MissingConfigurationException
  {
    try (FileOutputStream outputStream = new FileOutputStream(destination, true)) {
      downloadFromCdn(outputStream, destination.length(), cdnNumber, headers, path, maxSizeBytes, listener);
    }
  }

  private void downloadFromCdn(OutputStream outputStream, long offset, int cdnNumber, Map<String, String> headers, String path, long maxSizeBytes, ProgressListener listener)
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

    for (Map.Entry<String, String> header : headers.entrySet()) {
      request.addHeader(header.getKey(), header.getValue());
    }

    if (offset > 0) {
      Log.i(TAG, "Starting download from CDN with offset " + offset);
      request.addHeader("Range", "bytes=" + offset + "-");
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    try (Response response = call.execute()) {
      if (response.isSuccessful()) {
        ResponseBody body = response.body();

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
            if (listener.shouldCancel()) {
              call.cancel();
              throw new PushNetworkException("Canceled by listener check.");
            }
          }
        }
      } else if (response.code() == 416) {
        throw new RangeException(offset);
      } else {
        throw new NonSuccessfulResponseCodeException(response.code(), "Response: " + response);
      }
    } catch (NonSuccessfulResponseCodeException | PushNetworkException e) {
      throw e;
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }
  }

  @Nullable
  public ZonedDateTime getCdnLastModifiedTime(int cdnNumber, Map<String, String> headers, String path) throws MissingConfigurationException, PushNetworkException, NonSuccessfulResponseCodeException {
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

    for (Map.Entry<String, String> header : headers.entrySet()) {
      request.addHeader(header.getKey(), header.getValue());
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    try (Response response = call.execute()) {
      if (response.isSuccessful()) {
        String lastModified = response.header("Last-Modified");
        if (lastModified == null) {
          return null;
        }
        return ZonedDateTime.parse(lastModified, DateTimeFormatter.RFC_1123_DATE_TIME);
      } else {
        throw new NonSuccessfulResponseCodeException(response.code(), "Response: " + response);
      }
    } catch (NonSuccessfulResponseCodeException | PushNetworkException e) {
      throw e;
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }
  }

  private AttachmentDigest uploadToCdn0(String path, String acl, String key, String policy, String algorithm,
                                        String credential, String date, String signature,
                                        InputStream data, String contentType, long length, boolean incremental,
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

    DigestingRequestBody file = new DigestingRequestBody(data, outputStreamFactory, contentType, length, incremental, progressListener, cancelationSignal, 0);

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

    try (Response response = call.execute()) {
      if (response.isSuccessful()) return file.getAttachmentDigest();
      else                         throw new NonSuccessfulResponseCodeException(response.code(), "Response: " + response);
    } catch (PushNetworkException | NonSuccessfulResponseCodeException e) {
      throw e;
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }
  }

  public String getResumableUploadUrl(AttachmentUploadForm uploadForm) throws IOException {
    ConnectionHolder connectionHolder = getRandom(cdnClientsMap.get(uploadForm.cdn), random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    Request.Builder request = new Request.Builder().url(buildConfiguredUrl(connectionHolder, uploadForm.signedUploadLocation))
                                                   .post(RequestBody.create(null, ""));

    for (Map.Entry<String, String> header : uploadForm.headers.entrySet()) {
      if (!header.getKey().equalsIgnoreCase("host")) {
        request.header(header.getKey(), header.getValue());
      }
    }

    if (connectionHolder.getHostHeader().isPresent()) {
      request.header("host", connectionHolder.getHostHeader().get());
    }

    request.addHeader("Content-Length", "0");

    if (uploadForm.cdn == 2) {
      request.addHeader("Content-Type", "application/octet-stream");
    } else if (uploadForm.cdn == 3) {
      request.addHeader("Upload-Defer-Length", "1")
             .addHeader("Tus-Resumable", "1.0.0");
    } else {
      throw new AssertionError("Unknown CDN version: " + uploadForm.cdn);
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    try (Response response = call.execute()) {
      if (response.isSuccessful()) {
        return response.header("location");
      } else {
        throw new NonSuccessfulResponseCodeException(response.code(), "Response: " + response);
      }
    } catch (PushNetworkException | NonSuccessfulResponseCodeException e) {
      throw e;
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }
  }

  private AttachmentDigest uploadToCdn2(String resumableUrl, InputStream data, String contentType, long length, boolean incremental, OutputStreamFactory outputStreamFactory, ProgressListener progressListener, CancelationSignal cancelationSignal) throws IOException {
    ConnectionHolder connectionHolder = getRandom(cdnClientsMap.get(2), random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    ResumeInfo           resumeInfo = getResumeInfoCdn2(resumableUrl, length);
    DigestingRequestBody file       = new DigestingRequestBody(data, outputStreamFactory, contentType, length, incremental, progressListener, cancelationSignal, resumeInfo.contentStart);

    if (resumeInfo.contentStart == length) {
      Log.w(TAG, "Resume start point == content length");
      try (NowhereBufferedSink buffer = new NowhereBufferedSink()) {
        file.writeTo(buffer);
      }
      return file.getAttachmentDigest();
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

    try (Response response = call.execute()) {
      if (response.isSuccessful()) return file.getAttachmentDigest();
      else                         throw new NonSuccessfulResponseCodeException(response.code(), "Response: " + response);
    } catch (PushNetworkException | NonSuccessfulResponseCodeException e) {
      throw e;
    } catch (IOException e) {
      if (e instanceof StreamResetException) {
        throw e;
      }
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }
  }

  public void uploadBackupFile(AttachmentUploadForm uploadForm, String resumableUploadUrl, InputStream data, long dataLength) throws IOException {
    uploadBackupFile(uploadForm, resumableUploadUrl, data, dataLength, null);
  }

  public void uploadBackupFile(AttachmentUploadForm uploadForm, String resumableUploadUrl, InputStream data, long dataLength, ProgressListener progressListener) throws IOException {
    if (uploadForm.cdn == 2) {
      uploadToCdn2(resumableUploadUrl, data, "application/octet-stream", dataLength, false, new NoCipherOutputStreamFactory(), progressListener, null);
    } else {
      uploadToCdn3(resumableUploadUrl, data, "application/octet-stream", dataLength, false, new NoCipherOutputStreamFactory(), progressListener, null, uploadForm.headers);
    }
  }

  private AttachmentDigest uploadToCdn3(String resumableUrl,
                                        InputStream data,
                                        String contentType,
                                        long length,
                                        boolean incremental,
                                        OutputStreamFactory outputStreamFactory,
                                        ProgressListener progressListener,
                                        CancelationSignal cancelationSignal,
                                        Map<String, String> headers)
      throws IOException
  {
    ConnectionHolder connectionHolder = getRandom(cdnClientsMap.get(3), random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    ResumeInfo           resumeInfo = getResumeInfoCdn3(resumableUrl, headers);
    DigestingRequestBody file       = new DigestingRequestBody(data, outputStreamFactory, contentType, length, incremental, progressListener, cancelationSignal, resumeInfo.contentStart);

    if (resumeInfo.contentStart == length) {
      Log.w(TAG, "Resume start point == content length");
      try (NowhereBufferedSink buffer = new NowhereBufferedSink()) {
        file.writeTo(buffer);
      }
      return file.getAttachmentDigest();
    } else if (resumeInfo.contentStart != 0) {
      Log.w(TAG, "Resuming previous attachment upload");
    }

    Request.Builder request = new Request.Builder().url(buildConfiguredUrl(connectionHolder, resumableUrl))
                                                   .patch(file)
                                                   .addHeader("Upload-Offset", String.valueOf(resumeInfo.contentStart))
                                                   .addHeader("Upload-Length", String.valueOf(length))
                                                   .addHeader("Tus-Resumable", "1.0.0");

    for (Map.Entry<String, String> entry : headers.entrySet()) {
      request.addHeader(entry.getKey(), entry.getValue());
    }

    if (connectionHolder.getHostHeader().isPresent()) {
      request.header("host", connectionHolder.getHostHeader().get());
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    try (Response response = call.execute()) {
      if (response.isSuccessful()) {
        return file.getAttachmentDigest();
      } else {
        throw new NonSuccessfulResponseCodeException(response.code(), "Response: " + response);
      }
    } catch (PushNetworkException | NonSuccessfulResponseCodeException e) {
      throw e;
    } catch (IOException e) {
      if (e instanceof StreamResetException) {
        throw e;
      }
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }
  }

  private ResumeInfo getResumeInfoCdn2(String resumableUrl, long contentLength) throws IOException {
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

    try (Response response = call.execute()) {
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
        throw new NonSuccessfulResumableUploadResponseCodeException(response.code(), "Response: " + response);
      }
    } catch (PushNetworkException | NonSuccessfulResponseCodeException e) {
      throw e;
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }

    return new ResumeInfo(contentRange, offset);
  }

  private ResumeInfo getResumeInfoCdn3(String resumableUrl, Map<String, String> headers) throws IOException {
    ConnectionHolder connectionHolder = getRandom(cdnClientsMap.get(3), random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    final long   offset;

    Request.Builder request = new Request.Builder().url(buildConfiguredUrl(connectionHolder, resumableUrl))
                                                   .head()
                                                   .addHeader("Tus-Resumable", "1.0.0");

    for (Map.Entry<String, String> entry : headers.entrySet()) {
      request.addHeader(entry.getKey(), entry.getValue());
    }

    if (connectionHolder.getHostHeader().isPresent()) {
      request.header("host", connectionHolder.getHostHeader().get());
    }

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    try (Response response = call.execute()) {
      if (response.isSuccessful()) {
        offset = Long.parseLong(Objects.requireNonNull(response.header("Upload-Offset")));
      } else {
        throw new ResumeLocationInvalidException("Response: " + response);
      }
    } catch (PushNetworkException | NonSuccessfulResponseCodeException e) {
      throw e;
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }

    return new ResumeInfo(null, offset);
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
      throws NonSuccessfulResponseCodeException, PushNetworkException, MalformedResponseException
  {
    return makeServiceRequest(urlFragment, method, jsonBody, NO_HEADERS, NO_HANDLER, SealedSenderAccess.NONE);
  }

  private String makeServiceRequest(String urlFragment, String method, String jsonBody, Map<String, String> headers, ResponseCodeHandler responseCodeHandler, @Nullable SealedSenderAccess sealedSenderAccess)
      throws NonSuccessfulResponseCodeException, PushNetworkException, MalformedResponseException
  {
    try (Response response = makeServiceRequest(urlFragment, method, jsonRequestBody(jsonBody), headers, responseCodeHandler, sealedSenderAccess, false)) {
      return readBodyString(response);
    }
  }

  private static RequestBody jsonRequestBody(String jsonBody) {
    return jsonBody != null ? RequestBody.create(MediaType.parse("application/json"), jsonBody)
                            : null;
  }

  private static RequestBody protobufRequestBody(Message<?, ?> protobufBody) {
    return protobufBody != null ? RequestBody.create(MediaType.parse("application/x-protobuf"), protobufBody.encode())
                                : null;
  }

  private Response makeServiceRequest(String urlFragment,
                                      String method,
                                      RequestBody body,
                                      Map<String, String> headers,
                                      ResponseCodeHandler responseCodeHandler,
                                      @Nullable SealedSenderAccess sealedSenderAccess,
                                      boolean doNotAddAuthenticationOrUnidentifiedAccessKey)
      throws NonSuccessfulResponseCodeException, PushNetworkException, MalformedResponseException
  {
    Response response = null;
    try {
      response = getServiceConnection(urlFragment, method, body, headers, sealedSenderAccess, doNotAddAuthenticationOrUnidentifiedAccessKey);
      responseCodeHandler.handle(response.code(), response.body(), response::header);
      return validateServiceResponse(response);
    } catch (Exception e) {
      if (response != null && response.body() != null) {
        response.body().close();
      }
      throw e;
    }
  }

  private Response validateServiceResponse(Response response)
      throws NonSuccessfulResponseCodeException, PushNetworkException, MalformedResponseException {
    int    responseCode    = response.code();
    String responseMessage = response.message();

    switch (responseCode) {
      case 413:
      case 429: {
        long           retryAfterLong = Util.parseLong(response.header("Retry-After"), -1);
        Optional<Long> retryAfter     = retryAfterLong != -1 ? Optional.of(TimeUnit.SECONDS.toMillis(retryAfterLong)) : Optional.empty();
        throw new RateLimitException(responseCode, "Rate limit exceeded: " + responseCode, retryAfter);
      }
      case 401:
      case 403:
        throw new AuthorizationFailedException(responseCode, "Authorization failed!");
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
        RegistrationLockFailure accountLockFailure = readResponseJson(response, RegistrationLockFailure.class);

        throw new LockedException(accountLockFailure.length,
                                  accountLockFailure.timeRemaining,
                                  accountLockFailure.svr2Credentials,
                                  accountLockFailure.svr3Credentials);
      case 428:
        ProofRequiredResponse proofRequiredResponse = readResponseJson(response, ProofRequiredResponse.class);
        String                retryAfterRaw = response.header("Retry-After");
        long                  retryAfter    = Util.parseInt(retryAfterRaw, -1);

        throw new ProofRequiredException(proofRequiredResponse, retryAfter);

      case 499:
        throw new DeprecatedVersionException();

      case 508:
        throw new ServerRejectedException();
    }

    if (responseCode != 200 && responseCode != 202 && responseCode != 204 && responseCode != 207) {
      throw new NonSuccessfulResponseCodeException(responseCode, "Bad response: " + responseCode + " " + responseMessage);
    }

    return response;
  }

  private Response getServiceConnection(String urlFragment,
                                        String method,
                                        RequestBody body,
                                        Map<String, String> headers,
                                        @Nullable SealedSenderAccess sealedSenderAccess,
                                        boolean doNotAddAuthenticationOrUnidentifiedAccessKey)
      throws PushNetworkException
  {
    try {
      OkHttpClient okHttpClient = buildOkHttpClient(sealedSenderAccess != null);
      Call         call         = okHttpClient.newCall(buildServiceRequest(urlFragment, method, body, headers, sealedSenderAccess, doNotAddAuthenticationOrUnidentifiedAccessKey));

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

  private Request buildServiceRequest(String urlFragment,
                                      String method,
                                      RequestBody body,
                                      Map<String, String> headers,
                                      @Nullable SealedSenderAccess sealedSenderAccess,
                                      boolean doNotAddAuthenticationOrUnidentifiedAccessKey) {

    ServiceConnectionHolder connectionHolder = (ServiceConnectionHolder) getRandom(serviceClients, random);

    Request.Builder request = new Request.Builder();
    request.url(String.format("%s%s", connectionHolder.getUrl(), urlFragment));
    request.method(method, body);

    for (Map.Entry<String, String> header : headers.entrySet()) {
      request.addHeader(header.getKey(), header.getValue());
    }

    if (!headers.containsKey("Authorization") && !doNotAddAuthenticationOrUnidentifiedAccessKey) {
      if (sealedSenderAccess != null) {
        request.addHeader(sealedSenderAccess.getHeaderName(), sealedSenderAccess.getHeaderValue());
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

  private Response makeStorageRequest(String authorization, String path, String method, RequestBody body, ResponseCodeHandler responseCodeHandler)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    return makeStorageRequest(authorization, path, method, body, NO_HEADERS, responseCodeHandler);
  }

  private Response makeStorageRequest(String authorization, String path, String method, RequestBody body, Map<String, String> headers, ResponseCodeHandler responseCodeHandler)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    ConnectionHolder connectionHolder = getRandom(storageClients, random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
                                                        .newBuilder()
                                                        .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
                                                        .build();

    Request.Builder request = new Request.Builder().url(connectionHolder.getUrl() + path);
    request.method(method, body);

    if (connectionHolder.getHostHeader().isPresent()) {
      request.addHeader("Host", connectionHolder.getHostHeader().get());
    }

    if (authorization != null) {
      request.addHeader("Authorization", authorization);
    }

    for (Map.Entry<String, String> entry : headers.entrySet()) {
      request.addHeader(entry.getKey(), entry.getValue());
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

    try (ResponseBody responseBody = response.body()) {
      responseCodeHandler.handle(response.code(), responseBody, response::header);

      switch (response.code()) {
        case 204:
          throw new NoContentException("No content!");
        case 401:
        case 403:
          throw new AuthorizationFailedException(response.code(), "Authorization failed!");
        case 404:
          throw new NotFoundException("Not found");
        case 409:
          if (responseBody != null) {
            throw new ContactManifestMismatchException(readBodyBytes(responseBody));
          } else {
            throw new ConflictException();
          }
        case 429:
          throw new RateLimitException(response.code(), "Rate limit exceeded: " + response.code());
        case 499:
          throw new DeprecatedVersionException();
      }

      throw new NonSuccessfulResponseCodeException(response.code(), "Response: " + response);
    }
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
                                                                   Optional<Dns> dns,
                                                                   Optional<SignalProxy> proxy)
  {
    List<ServiceConnectionHolder> serviceConnectionHolders = new LinkedList<>();

    for (SignalUrl url : urls) {
      serviceConnectionHolders.add(new ServiceConnectionHolder(createConnectionClient(url, interceptors, dns, proxy),
                                                               createConnectionClient(url, interceptors, dns, proxy),
                                                               url.getUrl(), url.getHostHeader()));
    }

    return serviceConnectionHolders.toArray(new ServiceConnectionHolder[0]);
  }

  private static Map<Integer, ConnectionHolder[]> createCdnClientsMap(final Map<Integer, SignalCdnUrl[]> signalCdnUrlMap,
                                                                      final List<Interceptor> interceptors,
                                                                      final Optional<Dns> dns,
                                                                      final Optional<SignalProxy> proxy) {
    validateConfiguration(signalCdnUrlMap);
    final Map<Integer, ConnectionHolder[]> result = new HashMap<>();
    for (Map.Entry<Integer, SignalCdnUrl[]> entry : signalCdnUrlMap.entrySet()) {
      result.put(entry.getKey(),
                 createConnectionHolders(entry.getValue(), interceptors, dns, proxy));
    }
    return Collections.unmodifiableMap(result);
  }

  private static void validateConfiguration(Map<Integer, SignalCdnUrl[]> signalCdnUrlMap) {
    if (!signalCdnUrlMap.containsKey(0) || !signalCdnUrlMap.containsKey(2)) {
      throw new AssertionError("Configuration used to create PushServiceSocket must support CDN 0 and CDN 2");
    }
  }

  private static ConnectionHolder[] createConnectionHolders(SignalUrl[] urls, List<Interceptor> interceptors, Optional<Dns> dns, Optional<SignalProxy> proxy) {
    List<ConnectionHolder> connectionHolders = new LinkedList<>();

    for (SignalUrl url : urls) {
      connectionHolders.add(new ConnectionHolder(createConnectionClient(url, interceptors, dns, proxy), url.getUrl(), url.getHostHeader()));
    }

    return connectionHolders.toArray(new ConnectionHolder[0]);
  }

  private static OkHttpClient createConnectionClient(SignalUrl url, List<Interceptor> interceptors, Optional<Dns> dns, Optional<SignalProxy> proxy) {
    try {
      TrustManager[] trustManagers = BlacklistingTrustManager.createFor(url.getTrustStore());

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustManagers, null);

      OkHttpClient.Builder builder = new OkHttpClient.Builder()
                                                     .sslSocketFactory(new Tls12SocketFactory(context.getSocketFactory()), (X509TrustManager)trustManagers[0])
                                                     .connectionSpecs(url.getConnectionSpecs().orElse(Util.immutableList(ConnectionSpec.RESTRICTED_TLS)))
                                                     .dns(dns.orElse(Dns.SYSTEM));

      if (proxy.isPresent()) {
        builder.socketFactory(new TlsProxySocketFactory(proxy.get().getHost(), proxy.get().getPort(), dns));
      }

      builder.sslSocketFactory(new Tls12SocketFactory(context.getSocketFactory()), (X509TrustManager)trustManagers[0])
             .connectionSpecs(url.getConnectionSpecs().orElse(Util.immutableList(ConnectionSpec.RESTRICTED_TLS)))
             .build();

      builder.connectionPool(new ConnectionPool(5, 45, TimeUnit.SECONDS));

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
      String identifier = credentialsProvider.getAci() != null ? credentialsProvider.getAci().toString() : credentialsProvider.getE164();
      if (credentialsProvider.getDeviceId() != SignalServiceAddress.DEFAULT_DEVICE_ID) {
        identifier += "." + credentialsProvider.getDeviceId();
      }
      return "Basic " + Base64.encodeWithPadding((identifier + ":" + credentialsProvider.getPassword()).getBytes("UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  private ConnectionHolder getRandom(ConnectionHolder[] connections, SecureRandom random) {
    return connections[random.nextInt(connections.length)];
  }

  /**
   * Converts {@link IOException} on body byte reading to {@link PushNetworkException}.
   */
  private static byte[] readBodyBytes(Response response) throws PushNetworkException, MalformedResponseException {
    if (response.body() == null) {
      throw new MalformedResponseException("No body!");
    }

    try {
      return response.body().bytes();
    } catch (IOException e) {
      throw new PushNetworkException(e);
    }
  }

  private static byte[] readBodyBytes(@Nonnull ResponseBody responseBody) throws PushNetworkException {
    try {
      return responseBody.bytes();
    } catch (IOException e) {
      throw new PushNetworkException(e);
    }
  }

  /**
   * Converts {@link IOException} on body reading to {@link PushNetworkException}.
   */
  static String readBodyString(Response response) throws PushNetworkException, MalformedResponseException {
    return readBodyString(response.body());
  }

  /**
   * Converts {@link IOException} on body reading to {@link PushNetworkException}.
   */
  private static String readBodyString(ResponseBody body) throws PushNetworkException, MalformedResponseException {
    if (body == null) {
      throw new MalformedResponseException("No body!");
    }

    try {
      return body.string();
    } catch (IOException e) {
      throw new PushNetworkException(e);
    }
  }

  /**
   * Converts {@link IOException} on body reading to {@link PushNetworkException}.
   * {@link IOException} during json parsing is converted to a {@link MalformedResponseException}
   */
  private static <T> T readBodyJson(ResponseBody body, Class<T> clazz) throws PushNetworkException, MalformedResponseException {
    String json = readBodyString(body);

    try {
      return JsonUtil.fromJson(json, clazz);
    } catch (JsonProcessingException e) {
      Log.w(TAG, e);
      throw new MalformedResponseException("Unable to parse entity", e);
    } catch (IOException e) {
      throw new PushNetworkException(e);
    }
  }

  /**
   * Converts {@link IOException} on body reading to {@link PushNetworkException}.
   * {@link IOException} during json parsing is converted to a {@link NonSuccessfulResponseCodeException} with response code detail.
   */
  private static <T> T readResponseJson(Response response, Class<T> clazz)
      throws PushNetworkException, MalformedResponseException
  {
      return readBodyJson(response.body(), clazz);
  }


  public enum VerificationCodeTransport { SMS, VOICE }

  public static class RegistrationLockV2 {
    @JsonProperty
    private String registrationLock;

    public RegistrationLockV2() {}

    public RegistrationLockV2(String registrationLock) {
      this.registrationLock = registrationLock;
    }
  }

  public static class RegistrationLockFailure {
    @JsonProperty
    public int length;

    @JsonProperty
    public long timeRemaining;

    @JsonProperty("backupCredentials")
    public AuthCredentials svr1Credentials;

    @JsonProperty
    public AuthCredentials svr2Credentials;

    @JsonProperty
    public Svr3Credentials svr3Credentials;
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
    void handle(int responseCode, ResponseBody body, Function<String, String> getHeader) throws NonSuccessfulResponseCodeException, PushNetworkException;
  }

  private static class EmptyResponseCodeHandler implements ResponseCodeHandler {
    @Override
    public void handle(int responseCode, ResponseBody body, Function<String, String> getHeader) { }
  }

  /**
   * A {@link ResponseCodeHandler} that only throws {@link NonSuccessfulResponseCodeException} with the response body.
   * Any further processing is left to the caller.
   */
  private static class UnopinionatedResponseCodeHandler implements ResponseCodeHandler {
    @Override
    public void handle(int responseCode, ResponseBody body, Function<String, String> getHeader) throws NonSuccessfulResponseCodeException, PushNetworkException {
      if (responseCode < 200 || responseCode > 299) {
        String bodyString = null;
        if (body != null) {
          try {
            bodyString = readBodyString(body);
          } catch (MalformedResponseException e) {
            Log.w(TAG, "Failed to read body string", e);
          }
        }

        throw new NonSuccessfulResponseCodeException(responseCode, "Response: " + responseCode, bodyString);
      }
    }
  }

  /**
   * A {@link ResponseCodeHandler} that only throws {@link NonSuccessfulResponseCodeException} with the response body.
   * Any further processing is left to the caller.
   */
  private static class UnopinionatedBinaryErrorResponseCodeHandler implements ResponseCodeHandler {
    @Override
    public void handle(int responseCode, ResponseBody body, Function<String, String> getHeader) throws NonSuccessfulResponseCodeException, PushNetworkException {
      if (responseCode < 200 || responseCode > 299) {
        byte[] bodyBytes = null;
        if (body != null) {
          bodyBytes = readBodyBytes(body);
        }

        throw new NonSuccessfulResponseCodeException(responseCode, "Response: " + responseCode, bodyBytes);
      }
    }
  }

  private static final ResponseCodeHandler GROUPS_V2_PUT_RESPONSE_HANDLER   = (responseCode, body, getHeader) -> {
    if (getHeader.apply("X-Signal-Timestamp") == null) {
      throw new NonSuccessfulResponseCodeException(500, "Missing timestamp header");
    }

    if (responseCode == 409) throw new GroupExistsException();
  };

  private static final ResponseCodeHandler GROUPS_V2_GET_CURRENT_HANDLER    = (responseCode, body, getHeader) -> {
    if (getHeader.apply("X-Signal-Timestamp") == null) {
      throw new NonSuccessfulResponseCodeException(500, "Missing timestamp header");
    }

    switch (responseCode) {
      case 403: throw new NotInGroupException();
      case 404: throw new GroupNotFoundException();
    }
  };

  private static final ResponseCodeHandler GROUPS_V2_PATCH_RESPONSE_HANDLER = (responseCode, body, getHeader) -> {
    if (getHeader.apply("X-Signal-Timestamp") == null) {
      throw new NonSuccessfulResponseCodeException(500, "Missing timestamp header");
    }

    if (responseCode == 400) throw new GroupPatchNotAcceptedException();
  };

  private static final ResponseCodeHandler GROUPS_V2_GET_JOIN_INFO_HANDLER  = (responseCode, body, getHeader) -> {
    if (getHeader.apply("X-Signal-Timestamp") == null) {
      throw new NonSuccessfulResponseCodeException(500, "Missing timestamp header");
    }

    if (responseCode == 403) {
      throw new ForbiddenException(Optional.ofNullable(getHeader.apply("X-Signal-Forbidden-Reason")));
    }
  };

  public GroupResponse putNewGroupsV2Group(Group group, GroupsV2AuthorizationString authorization)
      throws NonSuccessfulResponseCodeException, PushNetworkException, IOException, MalformedResponseException
  {
    try (Response response = makeStorageRequest(authorization.toString(),
                                                GROUPSV2_GROUP,
                                                "PUT",
                                                protobufRequestBody(group),
                                                GROUPS_V2_PUT_RESPONSE_HANDLER))
    {
      return GroupResponse.ADAPTER.decode(readBodyBytes(response));
    }
  }

  public GroupResponse getGroupsV2Group(GroupsV2AuthorizationString authorization)
      throws NonSuccessfulResponseCodeException, PushNetworkException, IOException, MalformedResponseException
  {
    try (Response response = makeStorageRequest(authorization.toString(),
                                                GROUPSV2_GROUP,
                                                "GET",
                                                null,
                                                GROUPS_V2_GET_CURRENT_HANDLER))
    {
      return GroupResponse.ADAPTER.decode(readBodyBytes(response));
    }
  }

  public AvatarUploadAttributes getGroupsV2AvatarUploadForm(String authorization)
      throws NonSuccessfulResponseCodeException, PushNetworkException, IOException, MalformedResponseException
  {
    try (Response response = makeStorageRequest(authorization,
                                                GROUPSV2_AVATAR_REQUEST,
                                                "GET",
                                                null,
                                                NO_HANDLER))
    {
      return AvatarUploadAttributes.ADAPTER.decode(readBodyBytes(response));
    }
  }

  public GroupChangeResponse patchGroupsV2Group(GroupChange.Actions groupChange, String authorization, Optional<byte[]> groupLinkPassword)
      throws NonSuccessfulResponseCodeException, PushNetworkException, IOException, MalformedResponseException
  {
    String path;

    if (groupLinkPassword.isPresent()) {
      path = String.format(GROUPSV2_GROUP_PASSWORD, Base64.encodeUrlSafeWithoutPadding(groupLinkPassword.get()));
    } else {
      path = GROUPSV2_GROUP;
    }

    try (Response response = makeStorageRequest(authorization,
                                                path,
                                                "PATCH",
                                                protobufRequestBody(groupChange),
                                                GROUPS_V2_PATCH_RESPONSE_HANDLER))
    {
      return GroupChangeResponse.ADAPTER.decode(readBodyBytes(response));
    }
  }

  public GroupHistory getGroupHistory(int fromVersion, GroupsV2AuthorizationString authorization, int highestKnownEpoch, boolean includeFirstState, long sendEndorsementsExpirationMs)
      throws IOException
  {
    Map<String, String> headers = new HashMap<>();
    headers.put("Cached-Send-Endorsements", Long.toString(TimeUnit.MILLISECONDS.toSeconds(sendEndorsementsExpirationMs)));

    try (Response response = makeStorageRequest(authorization.toString(),
                                                String.format(Locale.US, GROUPSV2_GROUP_CHANGES, fromVersion, highestKnownEpoch, includeFirstState),
                                                "GET",
                                                null,
                                                headers,
                                                GROUPS_V2_GET_CURRENT_HANDLER))
    {

      if (response.body() == null) {
        throw new PushNetworkException("No body!");
      }

      GroupChanges groupChanges = GroupChanges.ADAPTER.decode(readBodyBytes(response));

      if (response.code() == 206) {
        String                 contentRangeHeader = response.header("Content-Range");
        Optional<ContentRange> contentRange       = ContentRange.parse(contentRangeHeader);

        if (contentRange.isPresent()) {
          Log.i(TAG, "Additional logs for group: " + contentRangeHeader);
          return new GroupHistory(groupChanges, contentRange);
        } else {
          Log.w(TAG, "Unable to parse Content-Range header: " + contentRangeHeader);
          throw new MalformedResponseException("Unable to parse content range header on 206");
        }
      }

      return new GroupHistory(groupChanges, Optional.empty());
    }
  }

  public int getGroupJoinedAtRevision(GroupsV2AuthorizationString authorization)
      throws IOException
  {
    try (Response response = makeStorageRequest(authorization.toString(),
                                                GROUPSV2_JOINED_AT,
                                                "GET",
                                                null,
                                                GROUPS_V2_GET_CURRENT_HANDLER))
    {
      return Member.ADAPTER.decode(readBodyBytes(response)).joinedAtRevision;
    }
  }

  public GroupJoinInfo getGroupJoinInfo(Optional<byte[]> groupLinkPassword, GroupsV2AuthorizationString authorization)
      throws NonSuccessfulResponseCodeException, PushNetworkException, IOException, MalformedResponseException
  {
    String passwordParam = groupLinkPassword.map(Base64::encodeUrlSafeWithoutPadding).orElse("");
    try (Response response = makeStorageRequest(authorization.toString(),
                                                String.format(GROUPSV2_GROUP_JOIN, passwordParam),
                                                "GET",
                                                null,
                                                GROUPS_V2_GET_JOIN_INFO_HANDLER))
    {
      return GroupJoinInfo.ADAPTER.decode(readBodyBytes(response));
    }
  }

  public GroupExternalCredential getGroupExternalCredential(GroupsV2AuthorizationString authorization)
      throws NonSuccessfulResponseCodeException, PushNetworkException, IOException, MalformedResponseException
  {
    try (Response response = makeStorageRequest(authorization.toString(),
                                                GROUPSV2_TOKEN,
                                                "GET",
                                                null,
                                                NO_HANDLER))
    {
      return GroupExternalCredential.ADAPTER.decode(readBodyBytes(response));
    }
  }

  private static class RegistrationSessionResponseHandler implements ResponseCodeHandler {

    @Override
    public void handle(int responseCode, ResponseBody body, Function<String, String> getHeader) throws NonSuccessfulResponseCodeException, PushNetworkException {

      if (responseCode == 403) {
        throw new IncorrectRegistrationRecoveryPasswordException();
      } else if (responseCode == 404) {
        throw new NoSuchSessionException();
      } else if (responseCode == 409) {
        RegistrationSessionMetadataResponse response;
        try {
          response = parseSessionMetadataResponse(body, getHeader);
        } catch (IOException e) {
          Log.w(TAG, "Unable to read response body.", e);
          throw new NonSuccessfulResponseCodeException(409);
        }

        if (response.getMetadata().getVerified()) {
          throw new AlreadyVerifiedException();
        } else if (response.getMetadata().pushChallengedRequired() || response.getMetadata().captchaRequired()) {
          throw new ChallengeRequiredException(response);
        } else {
          Log.i(TAG, "Received 409 in reg session handler that is not verified, with required information: " + String.join(", ", response.getMetadata().getRequestedInformation()));
          throw new HttpConflictException();
        }
      } else if (responseCode == 502) {
        VerificationCodeFailureResponseBody response;
        try {
          response = JsonUtil.fromJson(body.string(), VerificationCodeFailureResponseBody.class);
        } catch (IOException e) {
          Log.e(TAG, "Unable to read response body.", e);
          throw new NonSuccessfulResponseCodeException(responseCode);
        }
        throw new ExternalServiceFailureException(response.getPermanentFailure(), response.getReason());
      }
    }
  }

  /**
   * Error handler used exclusively for dealing with request verification code during registration flow.
   */
  private static class RequestVerificationCodeResponseHandler implements ResponseCodeHandler {

    @Override
    public void handle(int responseCode, ResponseBody body, Function<String, String> getHeader) throws NonSuccessfulResponseCodeException {
      if (responseCode == 400) {
        throw new MalformedRequestException();
      } else if (responseCode == 403) {
        throw new IncorrectRegistrationRecoveryPasswordException();
      } else if (responseCode == 404) {
        throw new NoSuchSessionException();
      } else if (responseCode == 409) {
        RegistrationSessionMetadataResponse response;
        try {
          response = parseSessionMetadataResponse(body, getHeader);
        } catch (IOException e) {
          Log.e(TAG, "Unable to read response body.", e);
          throw new NonSuccessfulResponseCodeException(409);
        }

        if (response.getMetadata().getVerified()) {
          throw new AlreadyVerifiedException();
        } else if (response.getMetadata().pushChallengedRequired() || response.getMetadata().captchaRequired()) {
          throw new ChallengeRequiredException(response);
        } else {
          Log.i(TAG, "Received 409 in for reg code request that is not verified, with required information: " + String.join(", ", response.getMetadata().getRequestedInformation()));
          throw new HttpConflictException();
        }
      } else if (responseCode == 418) {
        throw new InvalidTransportModeException();
      } else if (responseCode == 429) {
        RegistrationSessionMetadataResponse response;
        try {
          response = parseSessionMetadataResponse(body, getHeader);
        } catch (IOException e) {
          Log.w(TAG, "Unable to read response body.", e);
          throw new NonSuccessfulResponseCodeException(429);
        }

        throw new RequestVerificationCodeRateLimitException(response);
      } else if (responseCode == 440) {
        VerificationCodeFailureResponseBody response;
        try {
          response = JsonUtil.fromJson(body.string(), VerificationCodeFailureResponseBody.class);
        } catch (IOException e) {
          Log.e(TAG, "Unable to read response body.", e);
          throw new NonSuccessfulResponseCodeException(responseCode);
        }
        throw new ExternalServiceFailureException(response.getPermanentFailure(), response.getReason());
      }
    }
  }


  private static class PatchRegistrationSessionResponseHandler implements ResponseCodeHandler {

    @Override
    public void handle(int responseCode, ResponseBody body, Function<String, String> getHeader) throws NonSuccessfulResponseCodeException, PushNetworkException {
      switch (responseCode) {
        case 403:
          throw new TokenNotAcceptedException();
        case 404:
          throw new NoSuchSessionException();
        case 409:
          RegistrationSessionMetadataResponse response;
          try {
            response = parseSessionMetadataResponse(body, getHeader);
          } catch (IOException e) {
            Log.e(TAG, "Unable to read response body.", e);
            throw new NonSuccessfulResponseCodeException(409);
          }
          if (response.getMetadata().getVerified()) {
            throw new AlreadyVerifiedException();
          } else if (response.getMetadata().pushChallengedRequired() || response.getMetadata().captchaRequired()) {
            throw new ChallengeRequiredException(response);
          } else {
            Log.i(TAG, "Received 409 for patching reg session that is not verified, with required information: " + String.join(", ", response.getMetadata().getRequestedInformation()));
            throw new HttpConflictException();
          }
      }
    }
  }

  /**
   * Error response handler used exclusively for submitting a verification code during a registration session.
   */
  private static class SubmitVerificationCodeResponseHandler implements ResponseCodeHandler {
    @Override
    public void handle(int responseCode, ResponseBody body, Function<String, String> getHeader) throws NonSuccessfulResponseCodeException, PushNetworkException {

      switch (responseCode) {
        case 400:
          throw new InvalidTransportModeException();
        case 404:
          throw new NoSuchSessionException();
        case 409:
          RegistrationSessionMetadataJson sessionMetadata;
          try {
            sessionMetadata = JsonUtil.fromJson(body.string(), RegistrationSessionMetadataJson.class);
          } catch (IOException e) {
            Log.e(TAG, "Unable to read response body.", e);
            throw new NonSuccessfulResponseCodeException(409);
          }
          if (sessionMetadata.getVerified()) {
            throw new AlreadyVerifiedException();
          } else if (sessionMetadata.getNextVerificationAttempt() == null) {
            // Note: this explicitly requires Verified to be false
            throw new MustRequestNewCodeException();
          } else {
            Log.i(TAG, "Received 409 for reg code submission that is not verified, with required information: " + String.join(", ", sessionMetadata.getRequestedInformation()));
            throw new HttpConflictException();
          }
        case 429:
          RegistrationSessionMetadataResponse response;
          try {
            response = parseSessionMetadataResponse(body, getHeader);
          } catch (IOException e) {
            Log.w(TAG, "Unable to read response body.", e);
            throw new NonSuccessfulResponseCodeException(429);
          }

          throw new SubmitVerificationCodeRateLimitException(response);
        case 440:
          VerificationCodeFailureResponseBody codeFailureResponse;
          try {
            codeFailureResponse = JsonUtil.fromJson(body.string(), VerificationCodeFailureResponseBody.class);
          } catch (IOException e) {
            Log.e(TAG, "Unable to read response body.", e);
            throw new NonSuccessfulResponseCodeException(responseCode);
          }

          throw new ExternalServiceFailureException(codeFailureResponse.getPermanentFailure(), codeFailureResponse.getReason());
      }
    }
  }

  private static RegistrationSessionMetadataResponse parseSessionMetadataResponse(Response response) throws IOException {
    return parseSessionMetadataResponse(response.body(), response::header);
  }

  private static RegistrationSessionMetadataResponse parseSessionMetadataResponse(ResponseBody body, Function<String, String> getHeader) throws IOException {
    long                            retryAfterLong = Util.parseLong(getHeader.apply("Retry-After"), -1);
    Long                            retryAfterMs   = retryAfterLong != -1 ? TimeUnit.SECONDS.toMillis(retryAfterLong) : null;
    RegistrationSessionMetadataJson responseBody   = JsonUtil.fromJson(body.string(), RegistrationSessionMetadataJson.class);

    return new RegistrationSessionMetadataResponse(responseBody, System.currentTimeMillis(), retryAfterMs);
  }

  private static @Nonnull String urlEncode(@Nonnull String data) throws IOException {
    //noinspection CharsetObjectCanBeUsed
    return URLEncoder.encode(data, StandardCharsets.UTF_8.name());
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
