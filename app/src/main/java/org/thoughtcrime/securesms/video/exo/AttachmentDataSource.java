package org.thoughtcrime.securesms.video.exo;


import android.net.Uri;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.thoughtcrime.securesms.mms.PartAuthority;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AttachmentDataSource implements DataSource {

  private final DefaultDataSource defaultDataSource;
  private final PartDataSource    partDataSource;

  private DataSource dataSource;

  public AttachmentDataSource(DefaultDataSource defaultDataSource, PartDataSource partDataSource) {
    this.defaultDataSource = defaultDataSource;
    this.partDataSource    = partDataSource;
  }

  @Override
  public void addTransferListener(TransferListener transferListener) {
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    if (PartAuthority.isLocalUri(dataSpec.uri)) dataSource = partDataSource;
    else                                        dataSource = defaultDataSource;

    return dataSource.open(dataSpec);
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    return dataSource.read(buffer, offset, readLength);
  }

  @Override
  public Uri getUri() {
    return dataSource.getUri();
  }

  @Override
  public Map<String, List<String>> getResponseHeaders() {
    return Collections.emptyMap();
  }

  @Override
  public void close() throws IOException {
    dataSource.close();
  }
}
