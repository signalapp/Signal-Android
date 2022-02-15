package org.thoughtcrime.securesms.media;

import android.content.Context;
import android.media.MediaDataSource;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.thoughtcrime.securesms.attachments.AttachmentId;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.PartUriParser;
import org.thoughtcrime.securesms.providers.BlobProvider;

import java.io.IOException;

@RequiresApi(api = 23)
public final class DecryptableUriMediaInput {

  private DecryptableUriMediaInput() {
  }

  public static @NonNull MediaInput createForUri(@NonNull Context context, @NonNull Uri uri) throws IOException {

    if (BlobProvider.isAuthority(uri)) {
      return new MediaInput.MediaDataSourceMediaInput(BlobProvider.getInstance().getMediaDataSource(context, uri));
    }

    if (PartAuthority.isLocalUri(uri)) {
      return createForAttachmentUri(context, uri);
    }

    return new MediaInput.UriMediaInput(context, uri);
  }

  private static @NonNull MediaInput createForAttachmentUri(@NonNull Context context, @NonNull Uri uri) {
    AttachmentId partId = new PartUriParser(uri).getPartId();

    if (!partId.isValid()) {
      throw new AssertionError();
    }

    MediaDataSource mediaDataSource = SignalDatabase.attachments().mediaDataSourceFor(partId);

    if (mediaDataSource == null) {
      throw new AssertionError();
    }

    return new MediaInput.MediaDataSourceMediaInput(mediaDataSource);
  }
}
