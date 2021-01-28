/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api;

import org.signal.zkgroup.VerificationFailedException;
import org.signal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.zkgroup.profiles.ProfileKey;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream;
import org.whispersystems.signalservice.api.crypto.ProfileCipherInputStream;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceStickerManifest;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.push.SignalServiceEnvelopeEntity;
import org.whispersystems.signalservice.internal.push.SignalServiceMessagesResult;
import org.whispersystems.signalservice.internal.sticker.StickerProtos;
import org.whispersystems.signalservice.internal.util.StaticCredentialsProvider;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.internal.util.concurrent.FutureTransformers;
import org.whispersystems.signalservice.internal.util.concurrent.ListenableFuture;
import org.whispersystems.signalservice.internal.websocket.WebSocketConnection;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * The primary interface for receiving Signal Service messages.
 *
 * @author Moxie Marlinspike
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class SignalServiceMessageReceiver {

  private final PushServiceSocket          socket;
  private final SignalServiceConfiguration urls;
  private final CredentialsProvider        credentialsProvider;
  private final String                     signalAgent;
  private final ConnectivityListener       connectivityListener;
  private final SleepTimer                 sleepTimer;
  private final ClientZkProfileOperations  clientZkProfileOperations;

  /**
   * Construct a SignalServiceMessageReceiver.
   *
   * @param urls The URL of the Signal Service.
   * @param uuid The Signal Service UUID.
   * @param e164 The Signal Service phone number.
   * @param password The Signal Service user password.
   * @param signalingKey The 52 byte signaling key assigned to this user at registration.
   */
  public SignalServiceMessageReceiver(SignalServiceConfiguration urls,
                                      UUID uuid, String e164, String password,
                                      String signalingKey, String signalAgent,
                                      ConnectivityListener listener,
                                      SleepTimer timer,
                                      ClientZkProfileOperations clientZkProfileOperations,
                                      boolean automaticNetworkRetry)
  {
    this(urls, new StaticCredentialsProvider(uuid, e164, password, signalingKey), signalAgent, listener, timer, clientZkProfileOperations, automaticNetworkRetry);
  }

  /**
   * Construct a SignalServiceMessageReceiver.
   *
   * @param urls The URL of the Signal Service.
   * @param credentials The Signal Service user's credentials.
   */
  public SignalServiceMessageReceiver(SignalServiceConfiguration urls,
                                      CredentialsProvider credentials,
                                      String signalAgent,
                                      ConnectivityListener listener,
                                      SleepTimer timer,
                                      ClientZkProfileOperations clientZkProfileOperations,
                                      boolean automaticNetworkRetry)
  {
    this.urls                      = urls;
    this.credentialsProvider       = credentials;
    this.socket                    = new PushServiceSocket(urls, credentials, signalAgent, clientZkProfileOperations, automaticNetworkRetry);
    this.signalAgent               = signalAgent;
    this.connectivityListener      = listener;
    this.sleepTimer                = timer;
    this.clientZkProfileOperations = clientZkProfileOperations;
  }

  /**
   * Retrieves a SignalServiceAttachment.
   *
   * @param pointer The {@link SignalServiceAttachmentPointer}
   *                received in a {@link SignalServiceDataMessage}.
   * @param destination The download destination for this attachment.
   *
   * @return An InputStream that streams the plaintext attachment contents.
   * @throws IOException
   * @throws InvalidMessageException
   */
  public InputStream retrieveAttachment(SignalServiceAttachmentPointer pointer, File destination, long maxSizeBytes)
      throws IOException, InvalidMessageException, MissingConfigurationException {
    return retrieveAttachment(pointer, destination, maxSizeBytes, null);
  }

  public ListenableFuture<ProfileAndCredential> retrieveProfile(SignalServiceAddress address,
                                                                 Optional<ProfileKey> profileKey,
                                                                 Optional<UnidentifiedAccess> unidentifiedAccess,
                                                                 SignalServiceProfile.RequestType requestType)
  {
    Optional<UUID> uuid = address.getUuid();

    if (uuid.isPresent() && profileKey.isPresent()) {
      if (requestType == SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL) {
        return socket.retrieveVersionedProfileAndCredential(uuid.get(), profileKey.get(), unidentifiedAccess);
      } else {
        return FutureTransformers.map(socket.retrieveVersionedProfile(uuid.get(), profileKey.get(), unidentifiedAccess), profile -> {
          return new ProfileAndCredential(profile,
                                          SignalServiceProfile.RequestType.PROFILE,
                                          Optional.absent());
        });
      }
    } else {
      return FutureTransformers.map(socket.retrieveProfile(address, unidentifiedAccess), profile -> {
        return new ProfileAndCredential(profile,
                                        SignalServiceProfile.RequestType.PROFILE,
                                        Optional.absent());
      });
    }
  }

  public SignalServiceProfile retrieveProfileByUsername(String username, Optional<UnidentifiedAccess> unidentifiedAccess)
      throws IOException
  {
    return socket.retrieveProfileByUsername(username, unidentifiedAccess);
  }

  public InputStream retrieveProfileAvatar(String path, File destination, ProfileKey profileKey, long maxSizeBytes)
      throws IOException
  {
    socket.retrieveProfileAvatar(path, destination, maxSizeBytes);
    return new ProfileCipherInputStream(new FileInputStream(destination), profileKey);
  }

  public FileInputStream retrieveGroupsV2ProfileAvatar(String path, File destination, long maxSizeBytes)
      throws IOException
  {
    socket.retrieveProfileAvatar(path, destination, maxSizeBytes);
    return new FileInputStream(destination);
  }

  /**
   * Retrieves a SignalServiceAttachment.
   *
   * @param pointer The {@link SignalServiceAttachmentPointer}
   *                received in a {@link SignalServiceDataMessage}.
   * @param destination The download destination for this attachment. If this file exists, it is
   *                    assumed that this is previously-downloaded content that can be resumed.
   * @param listener An optional listener (may be null) to receive callbacks on download progress.
   *
   * @return An InputStream that streams the plaintext attachment contents.
   * @throws IOException
   * @throws InvalidMessageException
   */
  public InputStream retrieveAttachment(SignalServiceAttachmentPointer pointer, File destination, long maxSizeBytes, ProgressListener listener)
      throws IOException, InvalidMessageException, MissingConfigurationException {
    if (!pointer.getDigest().isPresent()) throw new InvalidMessageException("No attachment digest!");

    socket.retrieveAttachment(pointer.getCdnNumber(), pointer.getRemoteId(), destination, maxSizeBytes, listener);
    return AttachmentCipherInputStream.createForAttachment(destination, pointer.getSize().or(0), pointer.getKey(), pointer.getDigest().get());
  }

  public InputStream retrieveSticker(byte[] packId, byte[] packKey, int stickerId)
      throws IOException, InvalidMessageException
  {
    byte[] data = socket.retrieveSticker(packId, stickerId);
    return AttachmentCipherInputStream.createForStickerData(data, packKey);
  }

  /**
   * Retrieves a {@link SignalServiceStickerManifest}.
   *
   * @param packId The 16-byte packId that identifies the sticker pack.
   * @param packKey The 32-byte packKey that decrypts the sticker pack.
   * @return The {@link SignalServiceStickerManifest} representing the sticker pack.
   * @throws IOException
   * @throws InvalidMessageException
   */
  public SignalServiceStickerManifest retrieveStickerManifest(byte[] packId, byte[] packKey)
      throws IOException, InvalidMessageException
  {
    byte[] manifestBytes = socket.retrieveStickerManifest(packId);

    InputStream           cipherStream = AttachmentCipherInputStream.createForStickerData(manifestBytes, packKey);
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    Util.copy(cipherStream, outputStream);

    StickerProtos.Pack                             pack     = StickerProtos.Pack.parseFrom(outputStream.toByteArray());
    List<SignalServiceStickerManifest.StickerInfo> stickers = new ArrayList<>(pack.getStickersCount());
    SignalServiceStickerManifest.StickerInfo       cover    = pack.hasCover() ? new SignalServiceStickerManifest.StickerInfo(pack.getCover().getId(), pack.getCover().getEmoji(), pack.getCover().getContentType())
                                                                          : null;

    for (StickerProtos.Pack.Sticker sticker : pack.getStickersList()) {
      stickers.add(new SignalServiceStickerManifest.StickerInfo(sticker.getId(), sticker.getEmoji(), sticker.getContentType()));
    }

    return new SignalServiceStickerManifest(pack.getTitle(), pack.getAuthor(), cover, stickers);
  }

  /**
   * Creates a pipe for receiving SignalService messages.
   *
   * Callers must call {@link SignalServiceMessagePipe#shutdown()} when finished with the pipe.
   *
   * @return A SignalServiceMessagePipe for receiving Signal Service messages.
   */
  public SignalServiceMessagePipe createMessagePipe() {
    WebSocketConnection webSocket = new WebSocketConnection(urls.getSignalServiceUrls()[0].getUrl(),
                                                            urls.getSignalServiceUrls()[0].getTrustStore(),
                                                            Optional.of(credentialsProvider), signalAgent, connectivityListener,
                                                            sleepTimer,
                                                            urls.getNetworkInterceptors(),
                                                            urls.getDns());

    return new SignalServiceMessagePipe(webSocket, Optional.of(credentialsProvider), clientZkProfileOperations);
  }

  public SignalServiceMessagePipe createUnidentifiedMessagePipe() {
    WebSocketConnection webSocket = new WebSocketConnection(urls.getSignalServiceUrls()[0].getUrl(),
                                                            urls.getSignalServiceUrls()[0].getTrustStore(),
                                                            Optional.<CredentialsProvider>absent(), signalAgent, connectivityListener,
                                                            sleepTimer,
                                                            urls.getNetworkInterceptors(),
                                                            urls.getDns());

    return new SignalServiceMessagePipe(webSocket, Optional.of(credentialsProvider), clientZkProfileOperations);
  }

  public List<SignalServiceEnvelope> retrieveMessages() throws IOException {
    return retrieveMessages(new NullMessageReceivedCallback());
  }

  public List<SignalServiceEnvelope> retrieveMessages(MessageReceivedCallback callback)
      throws IOException
  {
    List<SignalServiceEnvelope> results       = new LinkedList<>();
    SignalServiceMessagesResult messageResult = socket.getMessages();

    for (SignalServiceEnvelopeEntity entity : messageResult.getEnvelopes()) {
      SignalServiceEnvelope envelope;

      if (entity.hasSource() && entity.getSourceDevice() > 0) {
        SignalServiceAddress address = new SignalServiceAddress(UuidUtil.parseOrNull(entity.getSourceUuid()), entity.getSourceE164());
        envelope = new SignalServiceEnvelope(entity.getType(),
                                             Optional.of(address),
                                             entity.getSourceDevice(),
                                             entity.getTimestamp(),
                                             entity.getMessage(),
                                             entity.getContent(),
                                             entity.getServerTimestamp(),
                                             messageResult.getServerDeliveredTimestamp(),
                                             entity.getServerUuid());
      } else {
        envelope = new SignalServiceEnvelope(entity.getType(),
                                             entity.getTimestamp(),
                                             entity.getMessage(),
                                             entity.getContent(),
                                             entity.getServerTimestamp(),
                                             messageResult.getServerDeliveredTimestamp(),
                                             entity.getServerUuid());
      }

      callback.onMessage(envelope);
      results.add(envelope);

      if (envelope.hasUuid()) socket.acknowledgeMessage(envelope.getUuid());
      else                    socket.acknowledgeMessage(entity.getSourceE164(), entity.getTimestamp());
    }

    return results;
  }

  public void setSoTimeoutMillis(long soTimeoutMillis) {
    socket.setSoTimeoutMillis(soTimeoutMillis);
  }

  public interface MessageReceivedCallback {
    public void onMessage(SignalServiceEnvelope envelope);
  }

  public static class NullMessageReceivedCallback implements MessageReceivedCallback {
    @Override
    public void onMessage(SignalServiceEnvelope envelope) {}
  }

}
