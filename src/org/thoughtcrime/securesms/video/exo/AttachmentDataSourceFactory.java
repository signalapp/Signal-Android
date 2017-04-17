package org.thoughtcrime.securesms.video.exo;


import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.thoughtcrime.securesms.crypto.MasterSecret;

public class AttachmentDataSourceFactory implements DataSource.Factory {

  private final Context      context;
  private final MasterSecret masterSecret;

  private final DefaultDataSourceFactory             defaultDataSourceFactory;
  private final TransferListener<? super DataSource> listener;

  public AttachmentDataSourceFactory(@NonNull Context context, @NonNull MasterSecret masterSecret,
                                     @NonNull DefaultDataSourceFactory defaultDataSourceFactory,
                                     @Nullable TransferListener<? super DataSource> listener)
  {
    this.context                  = context;
    this.masterSecret             = masterSecret;
    this.defaultDataSourceFactory = defaultDataSourceFactory;
    this.listener                 = listener;
  }

  @Override
  public AttachmentDataSource createDataSource() {
    return new AttachmentDataSource(defaultDataSourceFactory.createDataSource(),
                                    new PartDataSource(context, masterSecret, listener));
  }
}
