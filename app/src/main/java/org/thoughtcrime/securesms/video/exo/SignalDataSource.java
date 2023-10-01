package org.thoughtcrime.securesms.video.exo;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;

import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultDataSourceFactory;
import androidx.media3.datasource.TransferListener;

import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.providers.BlobProvider;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;

 /**
 * Go-to {@link DataSource} that handles all of our various types of video sources.
 * Will defer to other {@link DataSource}s depending on the URI.
 */
 @OptIn(markerClass = UnstableApi.class)
 public class SignalDataSource implements DataSource {

  private final DefaultDataSource defaultDataSource;
  private final PartDataSource    partDataSource;
  private final BlobDataSource    blobDataSource;
  private final ChunkedDataSource chunkedDataSource;

  private DataSource dataSource;

  public SignalDataSource(@NonNull DefaultDataSource defaultDataSource,
                          @NonNull PartDataSource partDataSource,
                          @NonNull BlobDataSource blobDataSource,
                          @Nullable ChunkedDataSource chunkedDataSource)
  {
    this.defaultDataSource = defaultDataSource;
    this.partDataSource    = partDataSource;
    this.blobDataSource    = blobDataSource;
    this.chunkedDataSource = chunkedDataSource;
  }

  @Override
  public void addTransferListener(@NonNull TransferListener transferListener) {
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    if (BlobProvider.isAuthority(dataSpec.uri)) {
      dataSource = blobDataSource;
    } else if (PartAuthority.isLocalUri(dataSpec.uri)) {
      dataSource = partDataSource;
    } else if (chunkedDataSource != null && isRemoteUri(dataSpec.uri)) {
      dataSource = chunkedDataSource;
    } else {
      dataSource = defaultDataSource;
    }

    return dataSource.open(dataSpec);
  }

  @Override
  public int read(@NonNull byte[] buffer, int offset, int readLength) throws IOException {
    return dataSource.read(buffer, offset, readLength);
  }

  @Override
  public @Nullable Uri getUri() {
    return dataSource.getUri();
  }

  @Override
  public @NonNull Map<String, List<String>> getResponseHeaders() {
    return Collections.emptyMap();
  }

  @Override
  public void close() throws IOException {
    dataSource.close();
  }

  private static boolean isRemoteUri(@Nullable Uri uri) {
    if (uri != null) {
      String scheme = uri.getScheme();
      return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    } else {
      return false;
    }
  }

  public static final class Factory implements DataSource.Factory {
    private final Context                  context;
    private final OkHttpClient             okHttpClient;
    private final TransferListener         listener;

    public Factory(@NonNull Context context,
                   @Nullable OkHttpClient okHttpClient,
                   @Nullable TransferListener listener)
    {
      this.context                  = context;
      this.okHttpClient             = okHttpClient;
      this.listener                 = listener;
    }

    @Override
    public @NonNull SignalDataSource createDataSource() {
      return new SignalDataSource(new DefaultDataSourceFactory(context, "GenericUserAgent", null).createDataSource(),
                                  new PartDataSource(listener),
                                  new BlobDataSource(context, listener),
                                  okHttpClient != null ? new ChunkedDataSource(okHttpClient, listener) : null);
    }
  }
}
