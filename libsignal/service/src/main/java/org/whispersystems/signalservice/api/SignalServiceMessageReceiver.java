/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api;

import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.zkgroup.profiles.ClientZkProfileOperations;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
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
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.configuration.SignalServiceConfiguration;
import org.whispersystems.signalservice.internal.push.IdentityCheckRequest;
import org.whispersystems.signalservice.internal.push.IdentityCheckResponse;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.push.SignalServiceEnvelopeEntity;
import org.whispersystems.signalservice.internal.push.SignalServiceMessagesResult;
import org.whispersystems.signalservice.internal.sticker.StickerProtos;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.internal.util.concurrent.FutureTransformers;
import org.whispersystems.signalservice.internal.util.concurrent.ListenableFuture;
import org.whispersystems.signalservice.internal.util.concurrent.SettableFuture;
import org.whispersystems.signalservice.internal.websocket.ResponseMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.annotation.Nonnull;

import io.reactivex.rxjava3.core.Single;

/**
 * The primary interface for receiving Signal Service messages.
 *
 * @author Moxie Marlinspike
 */
public class SignalServiceMessageReceiver {

  private final PushServiceSocket socket;

  /**
   * Construct a SignalServiceMessageReceiver.
   *
   * @param urls The URL of the Signal Service.
   * @param credentials The Signal Service user's credentials.
   */
  public SignalServiceMessageReceiver(SignalServiceConfiguration urls,
                                      CredentialsProvider credentials,
                                      String signalAgent,
                                      ClientZkProfileOperations clientZkProfileOperations,
                                      boolean automaticNetworkRetry)
  {
    this.socket = new PushServiceSocket(urls, credentials, signalAgent, clientZkProfileOperations, automaticNetworkRetry);
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
                                                                SignalServiceProfile.RequestType requestType,
                                                                Locale locale)
  {

    if (profileKey.isPresent()) {
      ACI aci;
      if (address.getServiceId() instanceof ACI) {
        aci = (ACI) address.getServiceId();
      } else {
        // We shouldn't ever have a profile key for a non-ACI.
        SettableFuture<ProfileAndCredential> result = new SettableFuture<>();
        result.setException(new ClassCastException("retrieving a versioned profile requires an ACI"));
        return result;
      }

      if (requestType == SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL) {
        return socket.retrieveVersionedProfileAndCredential(aci, profileKey.get(), unidentifiedAccess, locale);
      } else {
        return FutureTransformers.map(socket.retrieveVersionedProfile(aci, profileKey.get(), unidentifiedAccess, locale), profile -> {
          return new ProfileAndCredential(profile,
                                          SignalServiceProfile.RequestType.PROFILE,
                                          Optional.empty());
        });
      }
    } else {
      return FutureTransformers.map(socket.retrieveProfile(address, unidentifiedAccess, locale), profile -> {
        return new ProfileAndCredential(profile,
                                        SignalServiceProfile.RequestType.PROFILE,
                                        Optional.empty());
      });
    }
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

  public Single<ServiceResponse<IdentityCheckResponse>> performIdentityCheck(@Nonnull IdentityCheckRequest request, @Nonnull Optional<UnidentifiedAccess> unidentifiedAccess, @Nonnull ResponseMapper<IdentityCheckResponse> responseMapper) {
    return socket.performIdentityCheck(request, unidentifiedAccess, responseMapper);
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
    return AttachmentCipherInputStream.createForAttachment(destination, pointer.getSize().orElse(0), pointer.getKey(), pointer.getDigest().get(), pointer.getIncrementalDigest().orElse(new byte[0]));
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

    StickerProtos.Pack                             pack     = StickerProtos.Pack.parseFrom(Util.readFullyAsBytes(cipherStream));
    List<SignalServiceStickerManifest.StickerInfo> stickers = new ArrayList<>(pack.getStickersCount());
    SignalServiceStickerManifest.StickerInfo       cover    = pack.hasCover() ? new SignalServiceStickerManifest.StickerInfo(pack.getCover().getId(), pack.getCover().getEmoji(), pack.getCover().getContentType())
                                                                          : null;

    for (StickerProtos.Pack.Sticker sticker : pack.getStickersList()) {
      stickers.add(new SignalServiceStickerManifest.StickerInfo(sticker.getId(), sticker.getEmoji(), sticker.getContentType()));
    }

    return new SignalServiceStickerManifest(pack.getTitle(), pack.getAuthor(), cover, stickers);
  }

  public List<SignalServiceEnvelope> retrieveMessages(boolean allowStories, MessageReceivedCallback callback)
      throws IOException
  {
    List<SignalServiceEnvelope> results       = new LinkedList<>();
    SignalServiceMessagesResult messageResult = socket.getMessages(allowStories);

    for (SignalServiceEnvelopeEntity entity : messageResult.getEnvelopes()) {
      SignalServiceEnvelope envelope;

      if (entity.hasSource() && entity.getSourceDevice() > 0) {
        SignalServiceAddress address = new SignalServiceAddress(ServiceId.parseOrThrow(entity.getSourceUuid()), entity.getSourceE164());
        envelope = new SignalServiceEnvelope(entity.getType(),
                                             Optional.of(address),
                                             entity.getSourceDevice(),
                                             entity.getTimestamp(),
                                             entity.getContent(),
                                             entity.getServerTimestamp(),
                                             messageResult.getServerDeliveredTimestamp(),
                                             entity.getServerUuid(),
                                             entity.getDestinationUuid(),
                                             entity.isUrgent(),
                                             entity.isStory(),
                                             entity.getReportSpamToken());
      } else {
        envelope = new SignalServiceEnvelope(entity.getType(),
                                             entity.getTimestamp(),
                                             entity.getContent(),
                                             entity.getServerTimestamp(),
                                             messageResult.getServerDeliveredTimestamp(),
                                             entity.getServerUuid(),
                                             entity.getDestinationUuid(),
                                             entity.isUrgent(),
                                             entity.isStory(),
                                             entity.getReportSpamToken());
      }

      callback.onMessage(envelope);
      results.add(envelope);

      if (envelope.hasServerGuid()) {
        socket.acknowledgeMessage(envelope.getServerGuid());
      } else {
        socket.acknowledgeMessage(entity.getSourceE164(), entity.getTimestamp());
      }
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
