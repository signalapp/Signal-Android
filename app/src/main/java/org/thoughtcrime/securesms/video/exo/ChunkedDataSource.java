package org.thoughtcrime.securesms.video.exo;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import org.thoughtcrime.securesms.dependencies.AppDependencies;
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
@OptIn(markerClass = UnstableApi.class)
class ChunkedDataSource implements DataSource {

  private final OkHttpClient     okHttpClient;
  private final TransferListener transferListener;

  private DataSpec               dataSpec;
  private GiphyMp4Cache.ReadData cacheEntry;

  private volatile Exception exception;

  ChunkedDataSource(@NonNull OkHttpClient okHttpClient, @Nullable TransferListener listener) {
    this.okHttpClient     = okHttpClient;
    this.transferListener = listener;
  }

  @Override
  public void addTransferListener(@NonNull TransferListener transferListener) {
  }

  @Override
  public long open(@NonNull DataSpec dataSpec) throws IOException {
    this.dataSpec  = dataSpec;
    this.exception = null;

    if (cacheEntry != null) {
      cacheEntry.release();
    }

    // XXX Android can't handle all videos starting at once, so this randomly offsets them
    try {
      Thread.sleep((long) (Math.random() * 750));
    } catch (InterruptedException e) {
      // Exoplayer sometimes interrupts the thread
    }

    Context       context = AppDependencies.getApplication();
    GiphyMp4Cache cache   = AppDependencies.getGiphyMp4Cache();

    cacheEntry = cache.read(context, dataSpec.uri);

    if (cacheEntry == null) {
      CountDownLatch     countDownLatch = new CountDownLatch(1);
      ChunkedDataFetcher fetcher        = new ChunkedDataFetcher(okHttpClient);

      fetcher.fetch(this.dataSpec.uri.toString(), dataSpec.length, new ChunkedDataFetcher.Callback() {
        @Override
        public void onSuccess(InputStream stream) {
          try {
            cacheEntry = cache.write(context, dataSpec.uri, stream);
          } catch (IOException e) {
            exception = e;
          }
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

      if (cacheEntry == null) {
        throw new IOException("Timed out waiting for download.");
      }

      if (transferListener != null) {
        transferListener.onTransferStart(this, dataSpec, false);
      }

      if (dataSpec.length != C.LENGTH_UNSET && dataSpec.length - dataSpec.position <= 0) {
        throw new EOFException("No more data");
      }
    }

    return cacheEntry.getLength();
  }

  @Override
  public int read(@NonNull byte[] buffer, int offset, int readLength) throws IOException {
    int read = cacheEntry.getInputStream().read(buffer, offset, readLength);

    if (read > 0 && transferListener != null) {
      transferListener.onBytesTransferred(this, dataSpec, false, read);
    }

    return read;
  }

  @Override
  public @Nullable Uri getUri() {
    return dataSpec.uri;
  }

  @Override
  public void close() throws IOException {
    if (cacheEntry != null) {
      cacheEntry.release();
    }
    cacheEntry = null;
  }
}
