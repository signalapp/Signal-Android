/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.session.libsignal.service.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.session.libsignal.libsignal.IdentityKey;
import org.session.libsignal.libsignal.ecc.ECPublicKey;
import org.session.libsignal.utilities.logging.Log;
import org.session.libsignal.libsignal.util.Pair;
import org.session.libsignal.libsignal.util.guava.Optional;
import org.session.libsignal.service.api.crypto.UnidentifiedAccess;
import org.session.libsignal.service.api.messages.SignalServiceAttachment.ProgressListener;
import org.session.libsignal.service.api.profiles.SignalServiceProfile;
import org.session.libsignal.service.api.push.ContactTokenDetails;
import org.session.libsignal.service.api.push.SignalServiceAddress;
import org.session.libsignal.service.api.push.SignedPreKeyEntity;
import org.session.libsignal.service.api.push.exceptions.AuthorizationFailedException;
import org.session.libsignal.service.api.push.exceptions.CaptchaRequiredException;
import org.session.libsignal.service.api.push.exceptions.ExpectationFailedException;
import org.session.libsignal.service.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.session.libsignal.service.api.push.exceptions.NotFoundException;
import org.session.libsignal.service.api.push.exceptions.PushNetworkException;
import org.session.libsignal.service.api.push.exceptions.RateLimitException;
import org.session.libsignal.service.api.push.exceptions.RemoteAttestationResponseExpiredException;
import org.session.libsignal.service.api.push.exceptions.UnregisteredUserException;
import org.session.libsignal.service.api.util.CredentialsProvider;
import org.session.libsignal.service.api.util.Tls12SocketFactory;
import org.session.libsignal.service.internal.configuration.SignalServiceConfiguration;
import org.session.libsignal.service.internal.configuration.SignalUrl;
import org.session.libsignal.service.internal.contacts.entities.DiscoveryRequest;
import org.session.libsignal.service.internal.contacts.entities.DiscoveryResponse;
import org.session.libsignal.service.internal.contacts.entities.RemoteAttestationRequest;
import org.session.libsignal.service.internal.contacts.entities.RemoteAttestationResponse;
import org.session.libsignal.service.internal.push.exceptions.MismatchedDevicesException;
import org.session.libsignal.service.internal.push.exceptions.StaleDevicesException;
import org.session.libsignal.service.internal.push.http.DigestingRequestBody;
import org.session.libsignal.service.internal.push.http.OutputStreamFactory;
import org.session.libsignal.utilities.Base64;
import org.session.libsignal.service.internal.util.BlacklistingTrustManager;
import org.session.libsignal.utilities.Hex;
import org.session.libsignal.utilities.JsonUtil;
import org.session.libsignal.service.internal.util.Util;

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
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
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

  private static final String SENDER_CERTIFICATE_PATH   = "/v1/certificate/delivery";

  private static final String ATTACHMENT_DOWNLOAD_PATH  = "attachments/%d";
  private static final String ATTACHMENT_UPLOAD_PATH    = "attachments/";

  private static final String STICKER_MANIFEST_PATH     = "stickers/%s/manifest.proto";
  private static final String STICKER_PATH              = "stickers/%s/full/%d";

  private static final Map<String, String> NO_HEADERS = Collections.emptyMap();
  private static final ResponseCodeHandler NO_HANDLER = new EmptyResponseCodeHandler();

  private       long      soTimeoutMillis = TimeUnit.SECONDS.toMillis(30);
  private final Set<Call> connections     = new HashSet<Call>();

  private final ServiceConnectionHolder[]  serviceClients;
  private final ConnectionHolder[]         cdnClients;
  private final ConnectionHolder[]         contactDiscoveryClients;
  private final OkHttpClient               attachmentClient;

  private final CredentialsProvider credentialsProvider;
  private final String              userAgent;
  private final SecureRandom        random;

  public PushServiceSocket(SignalServiceConfiguration signalServiceConfiguration, CredentialsProvider credentialsProvider, String userAgent) {
    this.credentialsProvider               = credentialsProvider;
    this.userAgent                         = userAgent;
    this.serviceClients                    = createServiceConnectionHolders(signalServiceConfiguration.getSignalServiceUrls());
    this.cdnClients                        = createConnectionHolders(signalServiceConfiguration.getSignalCdnUrls());
    this.contactDiscoveryClients           = createConnectionHolders(signalServiceConfiguration.getSignalContactDiscoveryUrls());
    this.attachmentClient                  = createAttachmentClient();
    this.random                            = new SecureRandom();
  }

  public List<SignalServiceEnvelopeEntity> getMessages() throws IOException {
    String responseText = makeServiceRequest(String.format(MESSAGE_PATH, ""), "GET", null);
    return JsonUtil.fromJson(responseText, SignalServiceEnvelopeEntityList.class).getMessages();
  }

  public void acknowledgeMessage(String sender, long timestamp) throws IOException {
    makeServiceRequest(String.format(SENDER_ACK_MESSAGE_PATH, sender, timestamp), "DELETE", null);
  }

  public byte[] retrieveSticker(byte[] packId, int stickerId)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    String                hexPackId = Hex.toStringCondensed(packId);
    ByteArrayOutputStream output    = new ByteArrayOutputStream();

    downloadFromCdn(output, String.format(STICKER_PATH, hexPackId, stickerId), 1024 * 1024, null);

    return output.toByteArray();
  }

  public byte[] retrieveStickerManifest(byte[] packId)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    String                hexPackId = Hex.toStringCondensed(packId);
    ByteArrayOutputStream output    = new ByteArrayOutputStream();

    downloadFromCdn(output, String.format(STICKER_MANIFEST_PATH, hexPackId), 1024 * 1024, null);

    return output.toByteArray();
  }

  public SignalServiceProfile retrieveProfile(SignalServiceAddress target, Optional<UnidentifiedAccess> unidentifiedAccess)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    try {
      String response = makeServiceRequest(String.format(PROFILE_PATH, target.getNumber()), "GET", null, NO_HEADERS, unidentifiedAccess);
      return JsonUtil.fromJson(response, SignalServiceProfile.class);
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new NonSuccessfulResponseCodeException("Unable to parse entity");
    }
  }

  private void downloadFromCdn(OutputStream outputStream, String path, int maxSizeBytes, ProgressListener listener)
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

    Call call = okHttpClient.newCall(request.build());

    synchronized (connections) {
      connections.add(call);
    }

    Response response;

    try {
      response = call.execute();

      if (response.isSuccessful()) {
        ResponseBody body = response.body();

        if (body == null)                        throw new PushNetworkException("No response body!");
        if (body.contentLength() > maxSizeBytes) throw new PushNetworkException("Response exceeds max size!");

        InputStream  in     = body.byteStream();
        byte[]       buffer = new byte[32768];

        int read, totalRead = 0;

        while ((read = in.read(buffer, 0, buffer.length)) != -1) {
          outputStream.write(buffer, 0, read);
          if ((totalRead += read) > maxSizeBytes) throw new PushNetworkException("Response exceeded max size!");

          if (listener != null) {
            listener.onAttachmentProgress(body.contentLength(), totalRead);
          }
        }

        return;
      }
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } finally {
      synchronized (connections) {
        connections.remove(call);
      }
    }

    throw new NonSuccessfulResponseCodeException("Response: " + response);
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

        throw new LockedException(accountLockFailure.length, accountLockFailure.timeRemaining);
    }

    if (responseCode != 200 && responseCode != 204) {
        throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " +
                                                     responseMessage);
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

      Log.w(TAG, "Push service URL: " + connectionHolder.getUrl());
      Log.w(TAG, "Opening URL: " + String.format("%s%s", connectionHolder.getUrl(), urlFragment));

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

      if (unidentifiedAccess.isPresent()) {
        request.addHeader("Unidentified-Access-Key", Base64.encodeBytes(unidentifiedAccess.get().getUnidentifiedAccessKey()));
      } else if (credentialsProvider.getPassword() != null) {
        request.addHeader("Authorization", getAuthorizationHeader(credentialsProvider));
      }

      if (userAgent != null) {
        request.addHeader("X-Signal-Agent", userAgent);
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

  private Response makeContactDiscoveryRequest(String authorization, List<String> cookies, String path, String method, String body)
      throws PushNetworkException, NonSuccessfulResponseCodeException
  {
    ConnectionHolder connectionHolder = getRandom(contactDiscoveryClients, random);
    OkHttpClient     okHttpClient     = connectionHolder.getClient()
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

  private ServiceConnectionHolder[] createServiceConnectionHolders(SignalUrl[] urls) {
    List<ServiceConnectionHolder> serviceConnectionHolders = new LinkedList<ServiceConnectionHolder>();

    for (SignalUrl url : urls) {
      serviceConnectionHolders.add(new ServiceConnectionHolder(createConnectionClient(url),
                                                               createConnectionClient(url),
                                                               url.getUrl(), url.getHostHeader()));
    }

    return serviceConnectionHolders.toArray(new ServiceConnectionHolder[0]);
  }

  private ConnectionHolder[] createConnectionHolders(SignalUrl[] urls) {
    List<ConnectionHolder> connectionHolders = new LinkedList<ConnectionHolder>();

    for (SignalUrl url : urls) {
      connectionHolders.add(new ConnectionHolder(createConnectionClient(url), url.getUrl(), url.getHostHeader()));
    }

    return connectionHolders.toArray(new ConnectionHolder[0]);
  }

  private OkHttpClient createConnectionClient(SignalUrl url) {
    try {
      TrustManager[] trustManagers = BlacklistingTrustManager.createFor(url.getTrustStore());

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustManagers, null);

      return new OkHttpClient.Builder()
                             .sslSocketFactory(new Tls12SocketFactory(context.getSocketFactory()), (X509TrustManager)trustManagers[0])
                             .connectionSpecs(url.getConnectionSpecs().or(Util.immutableList(ConnectionSpec.RESTRICTED_TLS)))
                             .build();
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (KeyManagementException e) {
      throw new AssertionError(e);
    }
  }

  private OkHttpClient createAttachmentClient() {
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, null, null);

      TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init((KeyStore)null);

      return new OkHttpClient.Builder()
                             .sslSocketFactory(new Tls12SocketFactory(context.getSocketFactory()),
                                               (X509TrustManager)trustManagerFactory.getTrustManagers()[0])
                             .connectionSpecs(Util.immutableList(ConnectionSpec.RESTRICTED_TLS))
                             .build();
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    } catch (KeyManagementException e) {
      throw new AssertionError(e);
    } catch (KeyStoreException e) {
      throw new AssertionError(e);
    }
  }

  private String getAuthorizationHeader(CredentialsProvider credentialsProvider) {
    try {
      return "Basic " + Base64.encodeBytes((credentialsProvider.getUser() + ":" + credentialsProvider.getPassword()).getBytes("UTF-8"));
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

  private static class RegistrationLockFailure {
    @JsonProperty
    private int length;

    @JsonProperty
    private long timeRemaining;
  }

  private static class AttachmentDescriptor {
    @JsonProperty
    private long id;

    @JsonProperty
    private String location;

    public long getId() {
      return id;
    }

    public String getLocation() {
      return location;
    }
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
}
