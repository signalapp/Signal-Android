/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api;

import com.google.protobuf.ByteString;

import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.zkgroup.profiles.ProfileKey;
import org.signal.zkgroup.profiles.ProfileKeyCredential;
import org.signal.zkgroup.profiles.ProfileKeyCredentialRequest;
import org.signal.zkgroup.profiles.ProfileKeyCredentialRequestContext;
import org.signal.zkgroup.profiles.ProfileKeyVersion;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.util.Hex;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.internal.push.AttachmentV2UploadAttributes;
import org.whispersystems.signalservice.internal.push.AttachmentV3UploadAttributes;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessageList;
import org.whispersystems.signalservice.internal.push.SendMessageResponse;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.internal.util.concurrent.FutureTransformers;
import org.whispersystems.signalservice.internal.util.concurrent.ListenableFuture;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;
import org.whispersystems.signalservice.internal.websocket.WebsocketResponse;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketRequestMessage;
import static org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketResponseMessage;

/**
 * A SignalServiceMessagePipe represents a dedicated connection
 * to the Signal Service, which the server can push messages
 * down through.
 */
public class SignalServiceMessagePipe {

  private static final String TAG = SignalServiceMessagePipe.class.getName();

  private static final String SERVER_DELIVERED_TIMESTAMP_HEADER = "X-Signal-Timestamp";

  private final WebSocketConnection           websocket;
  private final Optional<CredentialsProvider> credentialsProvider;
  private final ClientZkProfileOperations     clientZkProfile;

  SignalServiceMessagePipe(WebSocketConnection websocket,
                           Optional<CredentialsProvider> credentialsProvider,
                           ClientZkProfileOperations clientZkProfile)
  {
    this.websocket           = websocket;
    this.credentialsProvider = credentialsProvider;
    this.clientZkProfile     = clientZkProfile;

    this.websocket.connect();
  }

  /**
   * A blocking call that reads a message off the pipe.  When this
   * call returns, the message has been acknowledged and will not
   * be retransmitted.
   *
   * @param timeout The timeout to wait for.
   * @param unit The timeout time unit.
   * @return A new message.
   *
   * @throws InvalidVersionException
   * @throws IOException
   * @throws TimeoutException
   */
  public SignalServiceEnvelope read(long timeout, TimeUnit unit)
      throws InvalidVersionException, IOException, TimeoutException
  {
    return read(timeout, unit, new NullMessagePipeCallback());
  }

  /**
   * A blocking call that reads a message off the pipe (see {@link #read(long, java.util.concurrent.TimeUnit)}
   *
   * Unlike {@link #read(long, java.util.concurrent.TimeUnit)}, this method allows you
   * to specify a callback that will be called before the received message is acknowledged.
   * This allows you to write the received message to durable storage before acknowledging
   * receipt of it to the server.
   *
   * @param timeout The timeout to wait for.
   * @param unit The timeout time unit.
   * @param callback A callback that will be called before the message receipt is
   *                 acknowledged to the server.
   * @return The message read (same as the message sent through the callback).
   * @throws TimeoutException
   * @throws IOException
   * @throws InvalidVersionException
   */
  public SignalServiceEnvelope read(long timeout, TimeUnit unit, MessagePipeCallback callback)
      throws TimeoutException, IOException, InvalidVersionException
  {
    while (true) {
      Optional<SignalServiceEnvelope> envelope = readOrEmpty(timeout, unit, callback);

      if (envelope.isPresent()) {
        return envelope.get();
      }
    }
  }

  /**
   * Similar to {@link #read(long, TimeUnit, MessagePipeCallback)}, except this will return
   * {@link Optional#absent()} when an empty response is hit, which indicates the websocket is
   * empty.
   *
   * Important: The empty response will only be hit once for each connection. That means if you get
   * an empty response and call readOrEmpty() again on the same instance, you will not get an empty
   * response, and instead will block until you get an actual message. This will, however, reset if
   * connection breaks (if, for instance, you lose and regain network).
   */
  public Optional<SignalServiceEnvelope> readOrEmpty(long timeout, TimeUnit unit, MessagePipeCallback callback)
      throws TimeoutException, IOException, InvalidVersionException
  {
    if (!credentialsProvider.isPresent()) {
      throw new IllegalArgumentException("You can't read messages if you haven't specified credentials");
    }

    while (true) {
      WebSocketRequestMessage  request            = websocket.readRequest(unit.toMillis(timeout));
      WebSocketResponseMessage response           = createWebSocketResponse(request);
      boolean                  signalKeyEncrypted = isSignalKeyEncrypted(request);

      try {
        if (isSignalServiceEnvelope(request)) {
          Optional<String> timestampHeader = findHeader(request, SERVER_DELIVERED_TIMESTAMP_HEADER);
          long             timestamp       = 0;

          if (timestampHeader.isPresent()) {
            try {
              timestamp = Long.parseLong(timestampHeader.get());
            } catch (NumberFormatException e) {
              Log.w(TAG, "Failed to parse " + SERVER_DELIVERED_TIMESTAMP_HEADER);
            }
          }

          SignalServiceEnvelope envelope = new SignalServiceEnvelope(request.getBody().toByteArray(),
                                                                     credentialsProvider.get().getSignalingKey(),
                                                                     signalKeyEncrypted,
                                                                     timestamp);

          callback.onMessage(envelope);
          return Optional.of(envelope);
        } else if (isSocketEmptyRequest(request)) {
          return Optional.absent();
        }
      } finally {
        websocket.sendResponse(response);
      }
    }
  }

  public Future<SendMessageResponse> send(OutgoingPushMessageList list, Optional<UnidentifiedAccess> unidentifiedAccess) throws IOException {
    List<String> headers = new LinkedList<String>() {{
      add("content-type:application/json");
    }};

    if (unidentifiedAccess.isPresent()) {
      headers.add("Unidentified-Access-Key:" + Base64.encodeBytes(unidentifiedAccess.get().getUnidentifiedAccessKey()));
    }

    WebSocketRequestMessage requestMessage = WebSocketRequestMessage.newBuilder()
                                                                    .setId(new SecureRandom().nextLong())
                                                                    .setVerb("PUT")
                                                                    .setPath(String.format("/v1/messages/%s", list.getDestination()))
                                                                    .addAllHeaders(headers)
                                                                    .setBody(ByteString.copyFrom(JsonUtil.toJson(list).getBytes()))
                                                                    .build();

    ListenableFuture<WebsocketResponse> response = websocket.sendRequest(requestMessage);

    return FutureTransformers.map(response, value -> {
      if (value.getStatus() == 404) {
        throw new UnregisteredUserException(list.getDestination(), new NotFoundException("not found"));
      } else if (value.getStatus() == 508) {
        throw new ServerRejectedException();
      } else if (value.getStatus() < 200 || value.getStatus() >= 300) {
        throw new IOException("Non-successful response: " + value.getStatus());
      }

      if (Util.isEmpty(value.getBody())) {
        return new SendMessageResponse(false);
      } else {
        return JsonUtil.fromJson(value.getBody(), SendMessageResponse.class);
      }
    });
  }

  public ListenableFuture<ProfileAndCredential> getProfile(SignalServiceAddress address,
                                                           Optional<ProfileKey> profileKey,
                                                           Optional<UnidentifiedAccess> unidentifiedAccess,
                                                           SignalServiceProfile.RequestType requestType)
      throws IOException
  {
    List<String> headers = new LinkedList<>();

    if (unidentifiedAccess.isPresent()) {
      headers.add("Unidentified-Access-Key:" + Base64.encodeBytes(unidentifiedAccess.get().getUnidentifiedAccessKey()));
    }

    Optional<UUID>                     uuid           = address.getUuid();
    SecureRandom                       random         = new SecureRandom();
    ProfileKeyCredentialRequestContext requestContext = null;

    WebSocketRequestMessage.Builder builder = WebSocketRequestMessage.newBuilder()
                                                                     .setId(random.nextLong())
                                                                     .setVerb("GET")
                                                                     .addAllHeaders(headers);

    if (uuid.isPresent() && profileKey.isPresent()) {
      UUID              target               = uuid.get();
      ProfileKeyVersion profileKeyIdentifier = profileKey.get().getProfileKeyVersion(target);
      String            version              = profileKeyIdentifier.serialize();

      if (requestType == SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL) {
        requestContext = clientZkProfile.createProfileKeyCredentialRequestContext(random, target, profileKey.get());

        ProfileKeyCredentialRequest request           = requestContext.getRequest();
        String                      credentialRequest = Hex.toStringCondensed(request.serialize());

        builder.setPath(String.format("/v1/profile/%s/%s/%s", target, version, credentialRequest));
      } else {
        builder.setPath(String.format("/v1/profile/%s/%s", target, version));
      }
    } else {
      builder.setPath(String.format("/v1/profile/%s", address.getIdentifier()));
    }

    final ProfileKeyCredentialRequestContext finalRequestContext = requestContext;
    WebSocketRequestMessage requestMessage = builder.build();

    return FutureTransformers.map(websocket.sendRequest(requestMessage), response -> {
      if (response.getStatus() == 404) {
        throw new NotFoundException("Not found");
      } else if (response.getStatus() < 200 || response.getStatus() >= 300) {
        throw new NonSuccessfulResponseCodeException("Non-successful response: " + response.getStatus());
      }

      SignalServiceProfile signalServiceProfile = JsonUtil.fromJson(response.getBody(), SignalServiceProfile.class);
      ProfileKeyCredential profileKeyCredential = finalRequestContext != null && signalServiceProfile.getProfileKeyCredentialResponse() != null
                                                    ? clientZkProfile.receiveProfileKeyCredential(finalRequestContext, signalServiceProfile.getProfileKeyCredentialResponse())
                                                    : null;

      return new ProfileAndCredential(signalServiceProfile, requestType, Optional.fromNullable(profileKeyCredential));
    });
  }

  public AttachmentV2UploadAttributes getAttachmentV2UploadAttributes() throws IOException {
    try {
      WebSocketRequestMessage requestMessage = WebSocketRequestMessage.newBuilder()
                                                                      .setId(new SecureRandom().nextLong())
                                                                      .setVerb("GET")
                                                                      .setPath("/v2/attachments/form/upload")
                                                                      .build();

      WebsocketResponse response = websocket.sendRequest(requestMessage).get(10, TimeUnit.SECONDS);

      if (response.getStatus() < 200 || response.getStatus() >= 300) {
        throw new IOException("Non-successful response: " + response.getStatus());
      }

      return JsonUtil.fromJson(response.getBody(), AttachmentV2UploadAttributes.class);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new IOException(e);
    }
  }

  public AttachmentV3UploadAttributes getAttachmentV3UploadAttributes() throws IOException {
    try {
      WebSocketRequestMessage requestMessage = WebSocketRequestMessage.newBuilder()
                                                                      .setId(new SecureRandom().nextLong())
                                                                      .setVerb("GET")
                                                                      .setPath("/v3/attachments/form/upload")
                                                                      .build();

      WebsocketResponse response = websocket.sendRequest(requestMessage).get(10, TimeUnit.SECONDS);

      if (response.getStatus() < 200 || response.getStatus() >= 300) {
        throw new IOException("Non-successful response: " + response.getStatus());
      }

      return JsonUtil.fromJson(response.getBody(), AttachmentV3UploadAttributes.class);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new IOException(e);
    }
  }

  /**
   * Close this connection to the server.
   */
  public void shutdown() {
    websocket.disconnect();
  }

  private boolean isSignalServiceEnvelope(WebSocketRequestMessage message) {
    return "PUT".equals(message.getVerb()) && "/api/v1/message".equals(message.getPath());
  }

  private boolean isSocketEmptyRequest(WebSocketRequestMessage message) {
    return "PUT".equals(message.getVerb()) && "/api/v1/queue/empty".equals(message.getPath());
  }

  private boolean isSignalKeyEncrypted(WebSocketRequestMessage message) {
    List<String> headers = message.getHeadersList();

    if (headers == null || headers.isEmpty()) {
      return true;
    }

    for (String header : headers) {
      String[] parts = header.split(":");

      if (parts.length == 2 && parts[0] != null && parts[0].trim().equalsIgnoreCase("X-Signal-Key")) {
        if (parts[1] != null && parts[1].trim().equalsIgnoreCase("false")) {
          return false;
        }
      }
    }

    return true;
  }

  private WebSocketResponseMessage createWebSocketResponse(WebSocketRequestMessage request) {
    if (isSignalServiceEnvelope(request)) {
      return WebSocketResponseMessage.newBuilder()
                                     .setId(request.getId())
                                     .setStatus(200)
                                     .setMessage("OK")
                                     .build();
    } else {
      return WebSocketResponseMessage.newBuilder()
                                     .setId(request.getId())
                                     .setStatus(400)
                                     .setMessage("Unknown")
                                     .build();
    }
  }

  private static Optional<String> findHeader(WebSocketRequestMessage message, String targetHeader) {
    if (message.getHeadersCount() == 0) {
      return Optional.absent();
    }

    for (String header : message.getHeadersList()) {
      if (header.startsWith(targetHeader)) {
        String[] split = header.split(":");
        if (split.length == 2 && split[0].trim().toLowerCase().equals(targetHeader.toLowerCase())) {
          return Optional.of(split[1].trim());
        }
      }
    }

    return Optional.absent();
  }

  /**
   * For receiving a callback when a new message has been
   * received.
   */
  public interface MessagePipeCallback {
    void onMessage(SignalServiceEnvelope envelope);
  }

  private static class NullMessagePipeCallback implements MessagePipeCallback {
    @Override
    public void onMessage(SignalServiceEnvelope envelope) {}
  }

}
