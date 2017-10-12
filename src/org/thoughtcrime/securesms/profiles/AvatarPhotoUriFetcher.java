package org.thoughtcrime.securesms.profiles;


import android.content.Context;
import android.support.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;

import org.thoughtcrime.securesms.database.Address;

import java.io.IOException;
import java.io.InputStream;

class AvatarPhotoUriFetcher implements DataFetcher<InputStream> {

  private final Context context;
  private final Address address;

  private InputStream inputStream;

  AvatarPhotoUriFetcher(@NonNull Context context, @NonNull Address address) {
    this.context = context.getApplicationContext();
    this.address = address;
  }

  @Override
  public void loadData(Priority priority, DataCallback<? super InputStream> callback) {
    try {
      inputStream = AvatarHelper.getInputStreamFor(context, address);
      callback.onDataReady(inputStream);
    } catch (IOException e) {
      callback.onLoadFailed(e);
    }
  }

  @Override
  public void cleanup() {
    try {
      if (inputStream != null) inputStream.close();
    } catch (IOException e) {}
  }

  @Override
  public void cancel() {

  }

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
