/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.session.libsignal.service.api;

import org.session.libsignal.libsignal.InvalidMessageException;
import org.session.libsignal.libsignal.util.guava.Optional;
import org.session.libsignal.service.api.crypto.AttachmentCipherInputStream;
import org.session.libsignal.service.api.crypto.ProfileCipherInputStream;
import org.session.libsignal.service.api.crypto.UnidentifiedAccess;
import org.session.libsignal.service.api.messages.SignalServiceAttachment.ProgressListener;
import org.session.libsignal.service.api.messages.SignalServiceAttachmentPointer;
import org.session.libsignal.service.api.messages.SignalServiceDataMessage;
import org.session.libsignal.service.api.messages.SignalServiceEnvelope;
import org.session.libsignal.service.api.messages.SignalServiceStickerManifest;
import org.session.libsignal.service.api.profiles.SignalServiceProfile;
import org.session.libsignal.service.api.push.SignalServiceAddress;
import org.session.libsignal.service.api.util.CredentialsProvider;
import org.session.libsignal.service.api.util.SleepTimer;
import org.session.libsignal.service.api.websocket.ConnectivityListener;
import org.session.libsignal.service.internal.configuration.SignalServiceConfiguration;
import org.session.libsignal.service.internal.push.PushServiceSocket;
import org.session.libsignal.service.internal.push.SignalServiceEnvelopeEntity;
import org.session.libsignal.service.internal.sticker.StickerProtos;
import org.session.libsignal.service.internal.util.StaticCredentialsProvider;
import org.session.libsignal.service.internal.util.Util;
import org.session.libsignal.service.internal.websocket.WebSocketConnection;
import org.session.libsignal.service.loki.utilities.DownloadUtilities;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

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
  private final String                     userAgent;
  private final ConnectivityListener       connectivityListener;
  private final SleepTimer                 sleepTimer;

  /**
   * Construct a SignalServiceMessageReceiver.
   *
   * @param urls The URL of the Signal Service.
   * @param user The Signal Service username (eg. phone number).
   * @param password The Signal Service user password.
   * @param signalingKey The 52 byte signaling key assigned to this user at registration.
   */
  public SignalServiceMessageReceiver(SignalServiceConfiguration urls,
                                      String user, String password,
                                      String signalingKey, String userAgent,
                                      ConnectivityListener listener,
                                      SleepTimer timer)
  {
    this(urls, new StaticCredentialsProvider(user, password, signalingKey), userAgent, listener, timer);
  }

  /**
   * Construct a SignalServiceMessageReceiver.
   *
   * @param urls The URL of the Signal Service.
   * @param credentials The Signal Service user's credentials.
   */
  public SignalServiceMessageReceiver(SignalServiceConfiguration urls,
                                      CredentialsProvider credentials,
                                      String userAgent,
                                      ConnectivityListener listener,
                                      SleepTimer timer)
  {
    this.urls                 = urls;
    this.credentialsProvider  = credentials;
    this.socket               = new PushServiceSocket(urls, credentials, userAgent);
    this.userAgent            = userAgent;
    this.connectivityListener = listener;
    this.sleepTimer           = timer;
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
  public InputStream retrieveAttachment(SignalServiceAttachmentPointer pointer, File destination, int maxSizeBytes)
      throws IOException, InvalidMessageException
  {
    return retrieveAttachment(pointer, destination, maxSizeBytes, null);
  }

  public InputStream retrieveProfileAvatar(String path, File destination, byte[] profileKey, int maxSizeBytes)
    throws IOException
  {
    DownloadUtilities.downloadFile(destination, path, maxSizeBytes, null);
    return new ProfileCipherInputStream(new FileInputStream(destination), profileKey);
  }

  /**
   * Retrieves a SignalServiceAttachment.
   *
   * @param pointer The {@link SignalServiceAttachmentPointer}
   *                received in a {@link SignalServiceDataMessage}.
   * @param destination The download destination for this attachment.
   * @param listener An optional listener (may be null) to receive callbacks on download progress.
   *
   * @return An InputStream that streams the plaintext attachment contents.
   * @throws IOException
   * @throws InvalidMessageException
   */
  public InputStream retrieveAttachment(SignalServiceAttachmentPointer pointer, File destination, int maxSizeBytes, ProgressListener listener)
      throws IOException, InvalidMessageException
  {
    // Loki - Fetch attachment
    if (pointer.getUrl().isEmpty()) throw new InvalidMessageException("Missing attachment URL.");
    DownloadUtilities.downloadFile(destination, pointer.getUrl(), maxSizeBytes, listener);

    // Loki - Assume we're retrieving an attachment for an open group server if the digest is not set
    if (!pointer.getDigest().isPresent()) { return new FileInputStream(destination); }

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
    List<SignalServiceStickerManifest.StickerInfo> stickers = new ArrayList<SignalServiceStickerManifest.StickerInfo>(pack.getStickersCount());
    SignalServiceStickerManifest.StickerInfo       cover    = pack.hasCover() ? new SignalServiceStickerManifest.StickerInfo(pack.getCover().getId(), pack.getCover().getEmoji())
                                                                          : null;

    for (StickerProtos.Pack.Sticker sticker : pack.getStickersList()) {
      stickers.add(new SignalServiceStickerManifest.StickerInfo(sticker.getId(), sticker.getEmoji()));
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
                                                            Optional.of(credentialsProvider), userAgent, connectivityListener,
                                                            sleepTimer);

    return new SignalServiceMessagePipe(webSocket, Optional.of(credentialsProvider));
  }

  public SignalServiceMessagePipe createUnidentifiedMessagePipe() {
    WebSocketConnection webSocket = new WebSocketConnection(urls.getSignalServiceUrls()[0].getUrl(),
                                                            urls.getSignalServiceUrls()[0].getTrustStore(),
                                                            Optional.<CredentialsProvider>absent(), userAgent, connectivityListener,
                                                            sleepTimer);

    return new SignalServiceMessagePipe(webSocket, Optional.of(credentialsProvider));
  }

  public List<SignalServiceEnvelope> retrieveMessages(MessageReceivedCallback callback)
      throws IOException
  {
    List<SignalServiceEnvelope>       results  = new LinkedList<SignalServiceEnvelope>();
    List<SignalServiceEnvelopeEntity> entities = socket.getMessages();

    for (SignalServiceEnvelopeEntity entity : entities) {
      SignalServiceEnvelope envelope;

      if (entity.getSource() != null && entity.getSourceDevice() > 0) {
        envelope = new SignalServiceEnvelope(entity.getType(), entity.getSource(),
                                             entity.getSourceDevice(), entity.getTimestamp(),
                                             entity.getContent(), entity.getServerTimestamp());
      } else {
        envelope = new SignalServiceEnvelope(entity.getType(), entity.getTimestamp(),
                                             entity.getContent(), entity.getServerTimestamp());
      }

      callback.onMessage(envelope);
      results.add(envelope);

      socket.acknowledgeMessage(entity.getSource(), entity.getTimestamp());
    }

    return results;
  }


  public interface MessageReceivedCallback {
    public void onMessage(SignalServiceEnvelope envelope);
  }

  public static class NullMessageReceivedCallback implements MessageReceivedCallback {
    @Override
    public void onMessage(SignalServiceEnvelope envelope) {}
  }

}
