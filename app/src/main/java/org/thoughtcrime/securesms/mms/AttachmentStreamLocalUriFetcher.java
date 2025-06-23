package org.thoughtcrime.securesms.mms;

import androidx.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

class AttachmentStreamLocalUriFetcher implements DataFetcher<InputStream> {

  private static final String TAG = Log.tag(AttachmentStreamLocalUriFetcher.class);

  private final File             attachment;
  private final byte[]           key;
  private final Optional<byte[]> digest;
  private final long             plaintextLength;

  private InputStream is;

  AttachmentStreamLocalUriFetcher(File attachment, long plaintextLength, byte[] key, Optional<byte[]> digest) {
    this.attachment              = attachment;
    this.plaintextLength         = plaintextLength;
    this.digest                  = digest;
    this.key                     = key;
  }

  @Override
  public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
    try {
      if (!digest.isPresent()) throw new InvalidMessageException("No attachment digest!");
      is = AttachmentCipherInputStream.createForAttachment(attachment,
                                                           plaintextLength,
                                                           key,
                                                           digest.get(),
                                                           null,
                                                           0);
      callback.onDataReady(is);
    } catch (IOException | InvalidMessageException e) {
      callback.onLoadFailed(e);
    }
  }

  @Override
  public void cleanup() {
    try {
      if (is != null) is.close();
      is = null;
    } catch (IOException ioe) {
      Log.w(TAG, "ioe");
    }
  }

  @Override
  public void cancel() {}

  @Override
  public @NonNull Class<InputStream> getDataClass() {
    return InputStream.class;
  }

  @Override
  public @NonNull DataSource getDataSource() {
    return DataSource.LOCAL;
  }


}
