package org.thoughtcrime.securesms.mms;

import android.content.ContentUris;
import android.content.Context;
import android.content.UriMatcher;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.avatar.AvatarPickerStorage;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.emoji.EmojiFiles;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.providers.DeprecatedPersistentBlobProvider;
import org.thoughtcrime.securesms.providers.PartProvider;
import org.thoughtcrime.securesms.wallpaper.WallpaperStorage;

import java.io.IOException;
import java.io.InputStream;

public class PartAuthority {

  private static final String AUTHORITY                 = BuildConfig.APPLICATION_ID;
  private static final String PART_URI_STRING           = "content://" + AUTHORITY + "/part";
  private static final String STICKER_URI_STRING        = "content://" + AUTHORITY + "/sticker";
  private static final String WALLPAPER_URI_STRING      = "content://" + AUTHORITY + "/wallpaper";
  private static final String EMOJI_URI_STRING          = "content://" + AUTHORITY + "/emoji";
  private static final String AVATAR_PICKER_URI_STRING  = "content://" + AUTHORITY + "/avatar_picker";
  private static final Uri    PART_CONTENT_URI          = Uri.parse(PART_URI_STRING);
  private static final Uri    STICKER_CONTENT_URI       = Uri.parse(STICKER_URI_STRING);
  private static final Uri    WALLPAPER_CONTENT_URI     = Uri.parse(WALLPAPER_URI_STRING);
  private static final Uri    EMOJI_CONTENT_URI         = Uri.parse(EMOJI_URI_STRING);
  private static final Uri    AVATAR_PICKER_CONTENT_URI = Uri.parse(AVATAR_PICKER_URI_STRING);

  private static final int PART_ROW          = 1;
  private static final int PERSISTENT_ROW    = 2;
  private static final int BLOB_ROW          = 3;
  private static final int STICKER_ROW       = 4;
  private static final int WALLPAPER_ROW     = 5;
  private static final int EMOJI_ROW         = 6;
  private static final int AVATAR_PICKER_ROW = 7;

  private static final UriMatcher uriMatcher;

  static {
    uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    uriMatcher.addURI(AUTHORITY, "part/*/#", PART_ROW);
    uriMatcher.addURI(AUTHORITY, "sticker/#", STICKER_ROW);
    uriMatcher.addURI(AUTHORITY, "wallpaper/*", WALLPAPER_ROW);
    uriMatcher.addURI(AUTHORITY, "emoji/*", EMOJI_ROW);
    uriMatcher.addURI(AUTHORITY, "avatar_picker/*", AVATAR_PICKER_ROW);
    uriMatcher.addURI(DeprecatedPersistentBlobProvider.AUTHORITY, DeprecatedPersistentBlobProvider.EXPECTED_PATH_OLD, PERSISTENT_ROW);
    uriMatcher.addURI(DeprecatedPersistentBlobProvider.AUTHORITY, DeprecatedPersistentBlobProvider.EXPECTED_PATH_NEW, PERSISTENT_ROW);
    uriMatcher.addURI(BlobProvider.AUTHORITY, BlobProvider.PATH, BLOB_ROW);
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
      case PERSISTENT_ROW:    return DeprecatedPersistentBlobProvider.getInstance(context).getStream(context, ContentUris.parseId(uri));
      case BLOB_ROW:          return BlobProvider.getInstance().getStream(context, uri);
      case WALLPAPER_ROW:     return WallpaperStorage.read(context, getWallpaperFilename(uri));
      case EMOJI_ROW:         return EmojiFiles.openForReading(context, getEmojiFilename(uri));
      case AVATAR_PICKER_ROW: return AvatarPickerStorage.read(context, getAvatarPickerFilename(uri));
      default:                return context.getContentResolver().openInputStream(uri);
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

      if (attachment != null) return attachment.getFileName();
      else                    return null;
    case PERSISTENT_ROW:
      return DeprecatedPersistentBlobProvider.getFileName(context, uri);
    case BLOB_ROW:
      return BlobProvider.getFileName(uri);
    default:
      return null;
    }
  }

  public static @Nullable Long getAttachmentSize(@NonNull Context context, @NonNull Uri uri) {
    int match = uriMatcher.match(uri);

    switch (match) {
      case PART_ROW:
        Attachment attachment = SignalDatabase.attachments().getAttachment(new PartUriParser(uri).getPartId());

        if (attachment != null) return attachment.getSize();
        else                    return null;
      case PERSISTENT_ROW:
        return DeprecatedPersistentBlobProvider.getFileSize(context, uri);
      case BLOB_ROW:
        return BlobProvider.getFileSize(uri);
      default:
        return null;
    }
  }

  public static @Nullable String getAttachmentContentType(@NonNull Context context, @NonNull Uri uri) {
    int match = uriMatcher.match(uri);

    switch (match) {
      case PART_ROW:
        Attachment attachment = SignalDatabase.attachments().getAttachment(new PartUriParser(uri).getPartId());

        if (attachment != null) return attachment.getContentType();
        else                    return null;
      case PERSISTENT_ROW:
        return DeprecatedPersistentBlobProvider.getMimeType(context, uri);
      case BLOB_ROW:
        return BlobProvider.getMimeType(uri);
      default:
        return null;
    }
  }

  public static boolean getAttachmentIsVideoGif(@NonNull Context context, @NonNull Uri uri) {
    int match = uriMatcher.match(uri);

    switch (match) {
      case PART_ROW:
        Attachment attachment = SignalDatabase.attachments().getAttachment(new PartUriParser(uri).getPartId());

        if (attachment != null) return attachment.isVideoGif();
        else                    return false;
      default:
        return false;
    }
  }

  public static @Nullable AttachmentTable.TransformProperties getAttachmentTransformProperties(@NonNull Uri uri) {
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
    Uri uri = Uri.withAppendedPath(PART_CONTENT_URI, String.valueOf(attachmentId.getUniqueId()));
    return ContentUris.withAppendedId(uri, attachmentId.getRowId());
  }

  public static Uri getAttachmentThumbnailUri(AttachmentId attachmentId) {
    return getAttachmentDataUri(attachmentId);
  }

  public static Uri getStickerUri(long id) {
    return ContentUris.withAppendedId(STICKER_CONTENT_URI, id);
  }

  public static Uri getWallpaperUri(String filename) {
    return Uri.withAppendedPath(WALLPAPER_CONTENT_URI, filename);
  }

  public static Uri getAvatarPickerUri(String filename) {
    return Uri.withAppendedPath(AVATAR_PICKER_CONTENT_URI, filename);
  }

  public static Uri getEmojiUri(String sprite) {
    return Uri.withAppendedPath(EMOJI_CONTENT_URI, sprite);
  }

  public static String getWallpaperFilename(Uri uri) {
    return uri.getPathSegments().get(1);
  }

  public static String getEmojiFilename(Uri uri) {
    return uri.getPathSegments().get(1);
  }

  public static String getAvatarPickerFilename(Uri uri) {
    return uri.getPathSegments().get(1);
  }

  public static boolean isLocalUri(final @NonNull Uri uri) {
    int match = uriMatcher.match(uri);
    switch (match) {
    case PART_ROW:
    case PERSISTENT_ROW:
    case BLOB_ROW:
      return true;
    }
    return false;
  }

  public static boolean isAttachmentUri(@NonNull Uri uri) {
    return uriMatcher.match(uri) == PART_ROW;
  }

  public static @NonNull AttachmentId requireAttachmentId(@NonNull Uri uri) {
    return new PartUriParser(uri).getPartId();
  }
}
