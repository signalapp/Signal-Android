package org.thoughtcrime.securesms.video.exo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

import okhttp3.OkHttpClient;

public class ChunkedDataSourceFactory implements DataSource.Factory {

  private final OkHttpClient     okHttpClient;
  private final TransferListener listener;

  public ChunkedDataSourceFactory(@NonNull OkHttpClient okHttpClient, @Nullable TransferListener listener) {
    this.okHttpClient = okHttpClient;
    this.listener     = listener;
  }


  @Override
  public DataSource createDataSource() {
    return new ChunkedDataSource(okHttpClient, listener);
  }
}
