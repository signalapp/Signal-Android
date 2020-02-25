package org.thoughtcrime.securesms.video;

import android.content.Context;
import android.media.MediaDataSource;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.PartUriParser;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.video.videoconverter.VideoInput;

import java.io.IOException;

@RequiresApi(api = 23)
public final class DecryptableUriVideoInput {

  private DecryptableUriVideoInput() {
  }

  public static VideoInput createForUri(@NonNull Context context, @NonNull Uri uri) throws IOException {

    if (BlobProvider.isAuthority(uri)) {
      return new VideoInput.MediaDataSourceVideoInput(BlobProvider.getInstance().getMediaDataSource(context, uri));
    }

    if (PartAuthority.isLocalUri(uri)) {
      return createForAttachmentUri(context, uri);
    }

    return new VideoInput.UriVideoInput(context, uri);
  }

  private static VideoInput createForAttachmentUri(@NonNull Context context, @NonNull Uri uri) {
    AttachmentId partId = new PartUriParser(uri).getPartId();

    if (!partId.isValid()) {
      throw new AssertionError();
    }

    MediaDataSource mediaDataSource = DatabaseFactory.getAttachmentDatabase(context)
                                                     .mediaDataSourceFor(partId);

    if (mediaDataSource == null) {
      throw new AssertionError();
    }

    return new VideoInput.MediaDataSourceVideoInput(mediaDataSource);
  }
}
