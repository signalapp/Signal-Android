package org.thoughtcrime.securesms.video.exo;


import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;

import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import org.thoughtcrime.securesms.providers.BlobProvider;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
class BlobDataSource implements DataSource {

  private final @NonNull  Context          context;
  private final @Nullable TransferListener listener;

  private DataSpec    dataSpec;
  private InputStream inputStream;

  BlobDataSource(@NonNull Context context, @Nullable TransferListener listener) {
    this.context  = context.getApplicationContext();
    this.listener = listener;
  }

  @Override
  public void addTransferListener(@NonNull TransferListener transferListener) {
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    this.dataSpec = dataSpec;
    this.inputStream = BlobProvider.getInstance().getStream(context, dataSpec.uri, dataSpec.position);

    if (listener != null) {
      listener.onTransferStart(this, dataSpec, false);
    }

    long size = unwrapLong(BlobProvider.getFileSize(dataSpec.uri));
    if (size == 0) {
      size = BlobProvider.getInstance().calculateFileSize(context, dataSpec.uri);
    }

    if (size - dataSpec.position <= 0) throw new EOFException("No more data");

    return size - dataSpec.position;
  }

  private long unwrapLong(@Nullable Long boxed) {
    return boxed == null ? 0L : boxed;
  }

  @Override
  public int read(@NonNull byte[] buffer, int offset, int readLength) throws IOException {
    int read = inputStream.read(buffer, offset, readLength);

    if (read > 0 && listener != null) {
      listener.onBytesTransferred(this, dataSpec, false, read);
    }

    return read;
  }

  @Override
  public Uri getUri() {
    return dataSpec.uri;
  }

  @Override
  public @NonNull Map<String, List<String>> getResponseHeaders() {
    return Collections.emptyMap();
  }

  @Override
  public void close() throws IOException {
    inputStream.close();
  }
}

