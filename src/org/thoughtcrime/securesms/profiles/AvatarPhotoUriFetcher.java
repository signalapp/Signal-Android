package org.thoughtcrime.securesms.profiles;


import android.content.Context;
import android.support.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.data.DataFetcher;

import org.thoughtcrime.securesms.database.Address;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class AvatarPhotoUriFetcher implements DataFetcher<InputStream> {

  private final Context context;
  private final Address address;

  private InputStream inputStream;

  public AvatarPhotoUriFetcher(@NonNull Context context, @NonNull Address address) {
    this.context = context.getApplicationContext();
    this.address = address;
  }

  @Override
  public InputStream loadData(Priority priority) throws IOException {
    inputStream = AvatarHelper.getInputStreamFor(context, address);
    return inputStream;
  }

  @Override
  public void cleanup() {
    try {
      if (inputStream != null) inputStream.close();
    } catch (IOException e) {}
  }

  @Override
  public String getId() {
    return address.serialize();
  }

  @Override
  public void cancel() {

  }
}
