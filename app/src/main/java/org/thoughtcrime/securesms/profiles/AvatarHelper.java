package org.thoughtcrime.securesms.profiles;


import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream;
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.ByteUnit;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.util.StreamDetails;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Iterator;

public class AvatarHelper {

  private static final String TAG = Log.tag(AvatarHelper.class);

  public static int  AVATAR_DIMENSIONS                 = 1024;
  public static long AVATAR_DOWNLOAD_FAILSAFE_MAX_SIZE = ByteUnit.MEGABYTES.toBytes(10);

  private static final String AVATAR_DIRECTORY = "avatars";

  /**
   * Retrieves an iterable set of avatars. Only intended to be used during backup.
   */
  public static Iterable<Avatar> getAvatars(@NonNull Context context) {
    File   avatarDirectory = context.getDir(AVATAR_DIRECTORY, Context.MODE_PRIVATE);
    File[] results         = avatarDirectory.listFiles();

    if (results == null) {
      return Collections.emptyList();
    }

    return () -> {
      return new Iterator<Avatar>() {
        int i = 0;
        @Override
        public boolean hasNext() {
          return i < results.length;
        }

        @Override
        public Avatar next() {
          File file = results[i];
          try {
            return new Avatar(getAvatar(context, RecipientId.from(file.getName())),
                              file.getName(),
                              ModernEncryptingPartOutputStream.getPlaintextLength(file.length()));
          } catch (IOException e) {
            return null;
          } finally {
            i++;
          }
        }
      };
    };
  }

  /**
   * Deletes and avatar.
   */
  public static void delete(@NonNull Context context, @NonNull RecipientId recipientId) {
    getAvatarFile(context, recipientId).delete();
  }

  /**
   * Whether or not an avatar is present for the given recipient.
   */
  public static boolean hasAvatar(@NonNull Context context, @NonNull RecipientId recipientId) {
    File avatarFile = getAvatarFile(context, recipientId);
    return avatarFile.exists() && avatarFile.length() > 0;
  }

  /**
   * Retrieves a stream for an avatar. If there is no avatar, the stream will likely throw an
   * IOException. It is recommended to call {@link #hasAvatar(Context, RecipientId)} first.
   */
  public static @NonNull InputStream getAvatar(@NonNull Context context, @NonNull RecipientId recipientId) throws IOException {
    AttachmentSecret attachmentSecret = AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();
    File             avatarFile       = getAvatarFile(context, recipientId);

    return ModernDecryptingPartInputStream.createFor(attachmentSecret, avatarFile, 0);
  }

  /**
   * Returns the size of the avatar on disk.
   */
  public static long getAvatarLength(@NonNull Context context, @NonNull RecipientId recipientId) {
    return ModernEncryptingPartOutputStream.getPlaintextLength(getAvatarFile(context, recipientId).length());
  }

  /**
   * Saves the contents of the input stream as the avatar for the specified recipient. If you pass
   * in null for the stream, the avatar will be deleted.
   */
  public static void setAvatar(@NonNull Context context, @NonNull RecipientId recipientId, @Nullable InputStream inputStream)
      throws IOException
  {
    if (inputStream == null) {
      delete(context, recipientId);
      return;
    }

    OutputStream outputStream = null;
    try {
      outputStream = getOutputStream(context, recipientId);
      Util.copy(inputStream, outputStream);
    } finally {
      Util.close(outputStream);
    }
  }

  /**
   * Retrieves an output stream you can write to that will be saved as the avatar for the specified
   * recipient. Only intended to be used for backup. Otherwise, use {@link #setAvatar(Context, RecipientId, InputStream)}.
   */
  public static @NonNull OutputStream getOutputStream(@NonNull Context context, @NonNull RecipientId recipientId) throws IOException {
    AttachmentSecret attachmentSecret = AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();
    File             targetFile       = getAvatarFile(context, recipientId);
    return ModernEncryptingPartOutputStream.createFor(attachmentSecret, targetFile, true).second;
  }

  /**
   * Returns the timestamp of when the avatar was last modified, or zero if the avatar doesn't exist.
   */
  public static long getLastModified(@NonNull Context context, @NonNull RecipientId recipientId) {
    File file = getAvatarFile(context, recipientId);

    if (file.exists()) {
      return file.lastModified();
    } else {
      return 0;
    }
  }

  /**
   * Returns a {@link StreamDetails} for the local user's own avatar, or null if one does not exist.
   */
  public static @Nullable StreamDetails getSelfProfileAvatarStream(@NonNull Context context) {
    RecipientId selfId = Recipient.self().getId();

    if (!hasAvatar(context, selfId)) {
      return null;
    }

    try {
      InputStream stream = getAvatar(context, selfId);
      return new StreamDetails(stream, MediaUtil.IMAGE_JPEG, getAvatarLength(context, selfId));
    } catch (IOException e) {
      Log.w(TAG,  "Failed to read own avatar!", e);
      return null;
    }
  }

  private static @NonNull File getAvatarFile(@NonNull Context context, @NonNull RecipientId recipientId) {
    File directory = context.getDir(AVATAR_DIRECTORY, Context.MODE_PRIVATE);
    return new File(directory, recipientId.serialize());
  }

  public static class Avatar {
    private final InputStream inputStream;
    private final String      filename;
    private final long        length;

    public Avatar(@NonNull InputStream inputStream, @NonNull String filename, long length) {
      this.inputStream = inputStream;
      this.filename    = filename;
      this.length      = length;
    }

    public @NonNull InputStream getInputStream() {
      return inputStream;
    }

    public @NonNull String getFilename() {
      return filename;
    }

    public long getLength() {
      return length;
    }
  }
}
