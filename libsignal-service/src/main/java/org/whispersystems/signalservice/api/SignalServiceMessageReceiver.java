/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api;

import org.signal.core.util.StreamUtil;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.backup.MediaRootBackupKey;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream.IntegrityCheck;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil;
import org.whispersystems.signalservice.api.crypto.ProfileCipherInputStream;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceStickerManifest;
import org.whispersystems.signalservice.api.push.exceptions.MissingConfigurationException;
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.sticker.Pack;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The primary interface for receiving Signal Service messages.
 *
 * @author Moxie Marlinspike
 */
public class SignalServiceMessageReceiver {

  private final PushServiceSocket socket;

  /**
   * Construct a SignalServiceMessageReceiver.
   */
  public SignalServiceMessageReceiver(PushServiceSocket socket) {
    this.socket = socket;
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
  public InputStream retrieveAttachment(SignalServiceAttachmentPointer pointer, File destination, long maxSizeBytes, IntegrityCheck integrityCheck)
      throws IOException, InvalidMessageException, MissingConfigurationException {
    return retrieveAttachment(pointer, destination, maxSizeBytes, integrityCheck, null);
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
   * Retrieves a SignalServiceAttachment. The encrypted data is written to @{code destination}, and then an {@link InputStream} is returned that decrypts the
   * contents of the destination file, giving you access to the plaintext content.
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
  public InputStream retrieveAttachment(SignalServiceAttachmentPointer pointer, File destination, long maxSizeBytes, IntegrityCheck integrityCheck, ProgressListener listener)
      throws IOException, InvalidMessageException, MissingConfigurationException {
    if (integrityCheck == null) throw new InvalidMessageException("No integrity check!");
    if (pointer.getKey() == null) throw new InvalidMessageException("No key!");

    socket.retrieveAttachment(pointer.getCdnNumber(), Collections.emptyMap(), pointer.getRemoteId(), destination, maxSizeBytes, listener);

    byte[] iv = new byte[16];
    try (InputStream tempStream = new FileInputStream(destination)) {
      StreamUtil.readFully(tempStream, iv);
    }

    return AttachmentCipherInputStream.createForAttachment(
      destination,
      pointer.getSize().orElse(0),
      pointer.getKey(),
      integrityCheck,
      null,
      0
    );
  }

  /**
   * Retrieves an archived media attachment.
   *
   * @param archivedMediaKeyMaterial Decryption key material for decrypting outer layer of archived media.
   * @param plaintextHash The plaintext hash of the attachment, used to verify the integrity of the downloaded content.
   * @param readCredentialHeaders Headers to pass to the backup CDN to authorize the download
   * @param archiveDestination The download destination for archived attachment. If this file exists, download will resume.
   * @param pointer The {@link SignalServiceAttachmentPointer} received in a {@link SignalServiceDataMessage}.
   * @param listener An optional listener (may be null) to receive callbacks on download progress.
   *
   * @return An InputStream that streams the plaintext attachment contents.
   */
  public InputStream retrieveArchivedAttachment(@Nonnull MediaRootBackupKey.MediaKeyMaterial archivedMediaKeyMaterial,
                                                @Nonnull byte[] plaintextHash,
                                                @Nonnull Map<String, String> readCredentialHeaders,
                                                @Nonnull File archiveDestination,
                                                @Nonnull SignalServiceAttachmentPointer pointer,
                                                long maxSizeBytes,
                                                @Nullable ProgressListener listener)
      throws IOException, InvalidMessageException, MissingConfigurationException
  {
    if (pointer.getKey() == null) {
      throw new InvalidMessageException("No key!");
    }

    socket.retrieveAttachment(pointer.getCdnNumber(), readCredentialHeaders, pointer.getRemoteId(), archiveDestination, maxSizeBytes, listener);

    long originalCipherLength = pointer.getSize()
                                       .filter(s -> s > 0)
                                       .map(s -> AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(s)))
                                       .orElse(0L);

    return AttachmentCipherInputStream.createForArchivedMedia(
        archivedMediaKeyMaterial,
        archiveDestination,
        originalCipherLength,
        pointer.getSize().orElse(0),
        pointer.getKey(),
        plaintextHash,
        null,
        0
    );
  }

  /**
   * Retrieves an archived media attachment.
   *
   * @param archivedMediaKeyMaterial Decryption key material for decrypting outer layer of archived media.
   * @param readCredentialHeaders Headers to pass to the backup CDN to authorize the download
   * @param archiveDestination The download destination for archived attachment. If this file exists, download will resume.
   * @param pointer The {@link SignalServiceAttachmentPointer} received in a {@link SignalServiceDataMessage}.
   * @param listener An optional listener (may be null) to receive callbacks on download progress.
   *
   * @return An InputStream that streams the plaintext attachment contents.
   */
  public InputStream retrieveArchivedThumbnail(@Nonnull MediaRootBackupKey.MediaKeyMaterial archivedMediaKeyMaterial,
                                               @Nonnull Map<String, String> readCredentialHeaders,
                                               @Nonnull File archiveDestination,
                                               @Nonnull SignalServiceAttachmentPointer pointer,
                                               long maxSizeBytes,
                                               @Nullable ProgressListener listener)
      throws IOException, InvalidMessageException, MissingConfigurationException
  {
    if (pointer.getKey() == null) {
      throw new InvalidMessageException("No key!");
    }

    socket.retrieveAttachment(pointer.getCdnNumber(), readCredentialHeaders, pointer.getRemoteId(), archiveDestination, maxSizeBytes, listener);

    long originalCipherLength = pointer.getSize()
                                       .filter(s -> s > 0)
                                       .map(s -> AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(s)))
                                       .orElse(0L);

    return AttachmentCipherInputStream.createForArchivedThumbnail(
        archivedMediaKeyMaterial,
        archiveDestination,
        originalCipherLength,
        pointer.getSize().orElse(0),
        pointer.getKey()
    );
  }

  public void retrieveBackup(int cdnNumber, Map<String, String> headers, String cdnPath, File destination, ProgressListener listener) throws MissingConfigurationException, IOException {
    socket.retrieveBackup(cdnNumber, headers, cdnPath, destination, 1_000_000_000L, listener);
  }

  @Nullable
  public ZonedDateTime getCdnLastModifiedTime(int cdnNumber, Map<String, String> headers, String cdnPath) throws MissingConfigurationException, IOException {
    return socket.getCdnLastModifiedTime(cdnNumber, headers, cdnPath);
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

    Pack                                           pack     = Pack.ADAPTER.decode(Util.readFullyAsBytes(cipherStream));
    List<SignalServiceStickerManifest.StickerInfo> stickers = new ArrayList<>(pack.stickers.size());
    SignalServiceStickerManifest.StickerInfo       cover    = pack.cover != null ? new SignalServiceStickerManifest.StickerInfo(pack.cover.id, pack.cover.emoji, pack.cover.contentType)
                                                                                 : null;

    for (Pack.Sticker sticker : pack.stickers) {
      stickers.add(new SignalServiceStickerManifest.StickerInfo(sticker.id, sticker.emoji, sticker.contentType));
    }

    return new SignalServiceStickerManifest(pack.title, pack.author, cover, stickers);
  }
}
