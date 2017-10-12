package org.thoughtcrime.securesms.mms;

import android.support.annotation.NonNull;
import android.util.Log;

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

  private File        attachment;
  private byte[]      key;
  private InputStream is;

  AttachmentStreamLocalUriFetcher(File attachment, byte[] key) {
    this.attachment = attachment;
    this.key        = key;
  }

  @Override
  public void loadData(Priority priority, DataCallback<? super InputStream> callback) {
    try {
      is = new AttachmentCipherInputStream(attachment, key, Optional.absent());
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
