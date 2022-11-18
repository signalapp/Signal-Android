package org.thoughtcrime.securesms.video.exo;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.thoughtcrime.securesms.net.ChunkedDataFetcher;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * DataSource which utilizes ChunkedDataFetcher to download video content via Signal content proxy.
 */
public class ChunkedDataSource implements DataSource {

  private final OkHttpClient     okHttpClient;
  private final TransferListener transferListener;

  private          Uri         uri;
  private volatile InputStream inputStream;
  private volatile Exception   exception;

  ChunkedDataSource(@NonNull OkHttpClient okHttpClient, @Nullable TransferListener listener) {
    this.okHttpClient     = okHttpClient;
    this.transferListener = listener;
  }

  @Override
  public void addTransferListener(TransferListener transferListener) {
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    this.uri       = dataSpec.uri;
    this.exception = null;

    if (inputStream != null) {
      inputStream.close();
    }

    this.inputStream = null;

    CountDownLatch     countDownLatch = new CountDownLatch(1);
    ChunkedDataFetcher fetcher        = new ChunkedDataFetcher(okHttpClient);

    fetcher.fetch(this.uri.toString(), dataSpec.length, new ChunkedDataFetcher.Callback() {
      @Override
      public void onSuccess(InputStream stream) {
        inputStream = stream;
        countDownLatch.countDown();
      }

      @Override
      public void onFailure(Exception e) {
        exception = e;
        countDownLatch.countDown();
      }
    });

    try {
      countDownLatch.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      throw new IOException(e);
    }

    if (exception != null) {
      throw new IOException(exception);
    }

    if (inputStream == null) {
      throw new IOException("Timed out waiting for input stream");
    }

    if (transferListener != null) {
      transferListener.onTransferStart(this, dataSpec, false);
    }

    if ( dataSpec.length != C.LENGTH_UNSET && dataSpec.length - dataSpec.position <= 0) {
      throw new EOFException("No more data");
    }

    return dataSpec.length;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    int read = inputStream.read(buffer, offset, readLength);

    if (read > 0 && transferListener != null) {
      transferListener.onBytesTransferred(this, null, false, read);
    }

    return read;
  }

  @Override
  public @Nullable Uri getUri() {
    return uri;
  }

  @Override
  public void close() throws IOException {
    if (inputStream != null) {
      inputStream.close();
    }
  }

}
