package org.thoughtcrime.securesms.profiles;


import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import org.thoughtcrime.securesms.database.Address;

import java.io.InputStream;
import java.security.MessageDigest;

public class AvatarPhotoUriLoader implements ModelLoader<AvatarPhotoUriLoader.AvatarPhotoUri, InputStream> {

  private final Context context;

  private AvatarPhotoUriLoader(Context context) {
    this.context = context;
  }

  @Nullable
  @Override
  public LoadData<InputStream> buildLoadData(AvatarPhotoUri avatarPhotoUri, int width, int height, Options options) {
    return new LoadData<>(avatarPhotoUri, new AvatarPhotoUriFetcher(context, avatarPhotoUri.address));
  }

  @Override
  public boolean handles(AvatarPhotoUri avatarPhotoUri) {
    return true;
  }

  public static class Factory implements ModelLoaderFactory<AvatarPhotoUri, InputStream> {

    private final Context context;

    public Factory(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public ModelLoader<AvatarPhotoUri, InputStream> build(MultiModelLoaderFactory multiFactory) {
      return new AvatarPhotoUriLoader(context);
    }

    @Override
    public void teardown() {}
  }

  public static class AvatarPhotoUri implements Key {
    public @NonNull Address address;

    public AvatarPhotoUri(@NonNull Address address) {
      this.address = address;
    }

    @Override
    public void updateDiskCacheKey(MessageDigest messageDigest) {
      messageDigest.update(address.serialize().getBytes());
    }

    @Override
    public boolean equals(Object other) {
      if (other == null || !(other instanceof  AvatarPhotoUri)) return false;

      return this.address.equals(((AvatarPhotoUri)other).address);
    }

    @Override
    public int hashCode() {
      return address.hashCode();
    }
  }

}
