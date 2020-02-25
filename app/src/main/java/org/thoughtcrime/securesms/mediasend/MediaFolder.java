package org.thoughtcrime.securesms.mediasend;

import android.net.Uri;
import androidx.annotation.NonNull;

/**
 * Represents a folder that's shown in {@link MediaPickerFolderFragment}.
 */
public class MediaFolder {

  private final Uri        thumbnailUri;
  private final String     title;
  private final int        itemCount;
  private final String     bucketId;
  private final FolderType folderType;

  MediaFolder(@NonNull Uri thumbnailUri, @NonNull String title, int itemCount, @NonNull String bucketId, @NonNull FolderType folderType) {
    this.thumbnailUri = thumbnailUri;
    this.title        = title;
    this.itemCount    = itemCount;
    this.bucketId     = bucketId;
    this.folderType   = folderType;
  }

  Uri getThumbnailUri() {
    return thumbnailUri;
  }

  public String getTitle() {
    return title;
  }

  int getItemCount() {
    return itemCount;
  }

  public String getBucketId() {
    return bucketId;
  }

  FolderType getFolderType() {
    return folderType;
  }

  enum FolderType {
    NORMAL, CAMERA
  }
}
