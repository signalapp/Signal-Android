package org.thoughtcrime.securesms.mms;

import android.support.annotation.NonNull;
import org.thoughtcrime.securesms.logging.Log;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;

import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherInputStream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

class AttachmentStreamLocalUriFetcher implements DataFetcher<InputStream> {

  private static final String TAG = AttachmentStreamLocalUriFetcher.class.getSimpleName();

  private final File             attachment;
  private final byte[]           key;
  private final Optional<byte[]> digest;
  private final long             plaintextLength;

  private InputStream is;

  AttachmentStreamLocalUriFetcher(File attachment, long plaintextLength, byte[] key, Optional<byte[]> digest) {
    this.attachment      = attachment;
    this.plaintextLength = plaintextLength;
    this.digest          = digest;
    this.key             = key;
  }

  @Override
  public void loadData(Priority priority, DataCallback<? super InputStream> callback) {
    try {
      if (!digest.isPresent()) throw new InvalidMessageException("No attachment digest!");
      is = AttachmentCipherInputStream.createFor(attachment, plaintextLength, key, digest.get());
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

  @NonNull
  @Override
  public Class<InputStream> getDataClass() {
    return InputStream.class;
  }

  @NonNull
  @Override
  public DataSource getDataSource() {
    return DataSource.LOCAL;
  }


}
