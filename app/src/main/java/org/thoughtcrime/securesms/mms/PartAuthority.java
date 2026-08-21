package org.thoughtcrime.securesms.mms;

import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.provider.DocumentsContractCompat;

import org.signal.core.models.database.AttachmentId;
import org.signal.core.models.media.TransformProperties;
import org.signal.core.util.PartAuthorityUris;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.avatar.AvatarPickerStorage;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.signal.emoji.EmojiFiles;
import org.thoughtcrime.securesms.providers.PartProvider;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class PartAuthority {

  private static final int PART_ROW          = 1;
  private static final int BLOB_ROW          = 3;
  private static final int STICKER_ROW       = 4;
  private static final int WALLPAPER_ROW     = 5;
  private static final int EMOJI_ROW         = 6;
  private static final int AVATAR_PICKER_ROW = 7;
  private static final int THUMBNAIL_ROW     = 8;

  private static final UriMatcher uriMatcher;

  static {
    String authority = PartAuthorityUris.getAuthority();

    uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    uriMatcher.addURI(authority, PartAuthorityUris.PATH_PART + "/#", PART_ROW);
    uriMatcher.addURI(authority, PartAuthorityUris.PATH_THUMBNAIL + "/#", THUMBNAIL_ROW);
    uriMatcher.addURI(authority, PartAuthorityUris.PATH_STICKER + "/#", STICKER_ROW);
    uriMatcher.addURI(authority, PartAuthorityUris.PATH_WALLPAPER + "/*", WALLPAPER_ROW);
    uriMatcher.addURI(authority, PartAuthorityUris.PATH_EMOJI + "/*", EMOJI_ROW);
    uriMatcher.addURI(authority, PartAuthorityUris.PATH_AVATAR_PICKER + "/*", AVATAR_PICKER_ROW);
    uriMatcher.addURI(AppDependencies.getBlobs().getAuthority(), AppDependencies.getBlobs().PATH, BLOB_ROW);
  }

  public static InputStream getAttachmentThumbnailStream(@NonNull Context context, @NonNull Uri uri)
      throws IOException
  {
    return getAttachmentStream(context, uri);
  }

  public static InputStream getAttachmentStream(@NonNull Context context, @NonNull Uri uri)
      throws IOException
  {
    int match = uriMatcher.match(uri);
    try {
      switch (match) {
      case PART_ROW:          return SignalDatabase.attachments().getAttachmentStream(new PartUriParser(uri).getPartId(), 0);
      case STICKER_ROW:       return SignalDatabase.stickers().getStickerStream(ContentUris.parseId(uri));
      case BLOB_ROW:          return AppDependencies.getBlobs().getStream(context, uri);
      case EMOJI_ROW:         return EmojiFiles.openForReading(context, getEmojiFilename(uri));
      case AVATAR_PICKER_ROW: return AvatarPickerStorage.read(context, getAvatarPickerFilename(uri));
      case THUMBNAIL_ROW:     return SignalDatabase.attachments().getAttachmentThumbnailStream(new PartUriParser(uri).getPartId(), 0);
      default:                return openExternalFileStream(context, uri);
      }
    } catch (SecurityException se) {
      throw new IOException(se);
    }
  }

  public static @Nullable String getAttachmentFileName(@NonNull Context context, @NonNull Uri uri) {
    int match = uriMatcher.match(uri);

    switch (match) {
    case PART_ROW:
      Attachment attachment = SignalDatabase.attachments().getAttachment(new PartUriParser(uri).getPartId());

      if (attachment != null) return attachment.fileName;
      else                    return null;
    case BLOB_ROW:
      return AppDependencies.getBlobs().getFileName(uri);
    default:
      return null;
    }
  }

  public static @Nullable Long getAttachmentSize(@NonNull Context context, @NonNull Uri uri) {
    int match = uriMatcher.match(uri);

    switch (match) {
      case PART_ROW:
        Attachment attachment = SignalDatabase.attachments().getAttachment(new PartUriParser(uri).getPartId());

        if (attachment != null) return attachment.size;
        else                    return null;
      case BLOB_ROW:
        return AppDependencies.getBlobs().getFileSize(uri);
      default:
        return null;
    }
  }

  public static @Nullable String getAttachmentContentType(@NonNull Context context, @NonNull Uri uri) {
    int match = uriMatcher.match(uri);

    switch (match) {
      case PART_ROW:
        Attachment attachment = SignalDatabase.attachments().getAttachment(new PartUriParser(uri).getPartId());

        if (attachment != null) return attachment.contentType;
        else                    return null;
      case BLOB_ROW:
        return AppDependencies.getBlobs().getMimeType(uri);
      default:
        return null;
    }
  }

  public static boolean getAttachmentIsVideoGif(@NonNull Context context, @NonNull Uri uri) {
    int match = uriMatcher.match(uri);

    switch (match) {
      case PART_ROW:
        Attachment attachment = SignalDatabase.attachments().getAttachment(new PartUriParser(uri).getPartId());

        if (attachment != null) return attachment.videoGif;
        else                    return false;
      default:
        return false;
    }
  }

  public static @Nullable TransformProperties getAttachmentTransformProperties(@NonNull Uri uri) {
    int match = uriMatcher.match(uri);
    switch (match) {
      case PART_ROW:
        return SignalDatabase.attachments().getTransformProperties(new PartUriParser(uri).getPartId());
      default:
        return null;
    }
  }

  public static Uri getAttachmentPublicUri(Uri uri) {
    PartUriParser partUri = new PartUriParser(uri);
    return PartProvider.getContentUri(partUri.getPartId());
  }

  public static Uri getAttachmentDataUri(AttachmentId attachmentId) {
    return PartAuthorityUris.getAttachmentDataUri(attachmentId);
  }

  public static Uri getAttachmentThumbnailUri(AttachmentId attachmentId) {
    return PartAuthorityUris.getAttachmentThumbnailUri(attachmentId);
  }

  public static Uri getStickerUri(long id) {
    return PartAuthorityUris.getStickerUri(id);
  }

  public static Uri getAvatarPickerUri(String filename) {
    return PartAuthorityUris.getAvatarPickerUri(filename);
  }

  public static Uri getEmojiUri(String sprite) {
    return PartAuthorityUris.getEmojiUri(sprite);
  }

  public static String getWallpaperFilename(Uri uri) {
    return PartAuthorityUris.getFilename(uri);
  }

  public static String getEmojiFilename(Uri uri) {
    return PartAuthorityUris.getFilename(uri);
  }

  public static String getAvatarPickerFilename(Uri uri) {
    return PartAuthorityUris.getFilename(uri);
  }

  public static boolean isLocalUri(final @NonNull Uri uri) {
    int match = uriMatcher.match(uri);
    switch (match) {
    case PART_ROW:
    case THUMBNAIL_ROW:
    case BLOB_ROW:
      return true;
    }
    return false;
  }

  public static boolean isAttachmentUri(@NonNull Uri uri) {
    int match = uriMatcher.match(uri);
    return match == PART_ROW || match == THUMBNAIL_ROW;
  }

  public static boolean isBlobUri(@NonNull Uri uri) {
    int match = uriMatcher.match(uri);
    return match == BLOB_ROW;
  }

  public static @NonNull AttachmentId requireAttachmentId(@NonNull Uri uri) {
    return new PartUriParser(uri).getPartId();
  }

  private static @Nullable InputStream openExternalFileStream(@NonNull Context context, @NonNull Uri uri) throws IOException {
    if (isVirtualFile(context, uri)) {
      return getInputStreamForVirtualFile(context, uri);
    } else {
      return context.getContentResolver().openInputStream(uri);
    }
  }

  private static boolean isVirtualFile(@NonNull Context context, @NonNull Uri uri) {
    if (!DocumentsContractCompat.isDocumentUri(context, uri)) {
      return false;
    }

    try (Cursor cursor = context.getContentResolver().query(uri, new String[]{DocumentsContract.Document.COLUMN_FLAGS}, null, null, null, null)) {
      if (cursor == null) {
        return false;
      }

      int flags = cursor.moveToFirst() ? cursor.getInt(0) : 0;
      return (flags & DocumentsContractCompat.DocumentCompat.FLAG_VIRTUAL_DOCUMENT) != 0;
    }
  }

  /** @noinspection resource*/
  private static @Nullable InputStream getInputStreamForVirtualFile(@NonNull Context context, @NonNull Uri uri) throws IOException {
    String[] openableMimeTypes = context.getContentResolver().getStreamTypes(uri, "*/*");

    if (openableMimeTypes == null || openableMimeTypes.length < 1) {
      throw new FileNotFoundException("No openable mime-types for virtual file.");
    }

    AssetFileDescriptor fileDescriptor = context.getContentResolver()
                                                .openTypedAssetFileDescriptor(uri, openableMimeTypes[0], null);

    if (fileDescriptor == null) {
      throw new FileNotFoundException("Couldn't open file descriptor for virtual file.");
    }

    return fileDescriptor.createInputStream();
  }
}
