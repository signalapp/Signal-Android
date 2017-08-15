package org.thoughtcrime.securesms.profiles;


import android.content.Context;
import android.support.annotation.NonNull;

import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.GenericLoaderFactory;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.stream.StreamModelLoader;

import org.thoughtcrime.securesms.database.Address;

import java.io.InputStream;

public class AvatarPhotoUriLoader implements StreamModelLoader<AvatarPhotoUriLoader.AvatarPhotoUri> {

  private final Context context;

  public static class Factory implements ModelLoaderFactory<AvatarPhotoUri, InputStream> {

    @Override
    public StreamModelLoader<AvatarPhotoUri> build(Context context, GenericLoaderFactory factories) {
      return new AvatarPhotoUriLoader(context);
    }

    @Override
    public void teardown() {}
  }

  public AvatarPhotoUriLoader(Context context) {
    this.context = context;
  }

  @Override
  public DataFetcher<InputStream> getResourceFetcher(AvatarPhotoUri model, int width, int height) {
    return new AvatarPhotoUriFetcher(context, model.address);
  }

  public static class AvatarPhotoUri {
    public @NonNull Address address;

    public AvatarPhotoUri(@NonNull Address address) {
      this.address = address;
    }
  }

}
