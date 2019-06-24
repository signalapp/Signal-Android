package org.thoughtcrime.securesms.glide;


import android.content.Context;
import androidx.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;

import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

class ContactPhotoFetcher implements DataFetcher<InputStream> {

  private final Context   context;
  private final ContactPhoto contactPhoto;

  private InputStream inputStream;

  ContactPhotoFetcher(@NonNull Context context, @NonNull ContactPhoto contactPhoto) {
    this.context      = context.getApplicationContext();
    this.contactPhoto = contactPhoto;
  }

  @Override
  public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
    try {
      inputStream = contactPhoto.openInputStream(context);
      callback.onDataReady(inputStream);
    } catch (FileNotFoundException e) {
      callback.onDataReady(null);
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

  @Override
  public @NonNull Class<InputStream> getDataClass() {
    return InputStream.class;
  }

  @Override
  public @NonNull DataSource getDataSource() {
    return DataSource.LOCAL;
  }
}
