package org.thoughtcrime.securesms.video.exo;


import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.thoughtcrime.securesms.providers.BlobProvider;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BlobDataSource implements DataSource {

  private final @NonNull  Context          context;
  private final @Nullable TransferListener listener;

  private Uri         uri;
  private InputStream inputStream;

  BlobDataSource(@NonNull Context context, @Nullable TransferListener listener) {
    this.context  = context.getApplicationContext();
    this.listener = listener;
  }

  @Override
  public void addTransferListener(TransferListener transferListener) {
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    this.uri         = dataSpec.uri;
    this.inputStream = BlobProvider.getInstance().getStream(context, uri, dataSpec.position);

    if (listener != null) {
      listener.onTransferStart(this, dataSpec, false);
    }

    long size = unwrapLong(BlobProvider.getFileSize(uri));
    if (size - dataSpec.position <= 0) throw new EOFException("No more data");

    return size - dataSpec.position;
  }

  private long unwrapLong(@Nullable Long boxed) {
    return boxed == null ? 0L : boxed;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    int read = inputStream.read(buffer, offset, readLength);

    if (read > 0 && listener != null) {
      listener.onBytesTransferred(this, null, false, read);
    }

    return read;
  }

  @Override
  public Uri getUri() {
    return uri;
  }

  @Override
  public Map<String, List<String>> getResponseHeaders() {
    return Collections.emptyMap();
  }

  @Override
  public void close() throws IOException {
    inputStream.close();
  }
}

