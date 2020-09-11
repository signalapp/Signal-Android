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

  MediaFolder(@NonNull Uri thumbnailUri, @NonNull String title, int itemCount, @NonNull String bucketId) {
    this.thumbnailUri = thumbnailUri;
    this.title        = title;
    this.itemCount    = itemCount;
    this.bucketId     = bucketId;
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

  enum FolderType {
    NORMAL, CAMERA
  }
}
