package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.StreamLocalUriFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import org.thoughtcrime.securesms.mms.ContactPhotoUriLoader.ContactPhotoUri;

import java.io.InputStream;
import java.security.MessageDigest;

public class ContactPhotoUriLoader implements ModelLoader<ContactPhotoUri, InputStream> {

  private final Context context;

  private ContactPhotoUriLoader(Context context) {
    this.context = context;
  }

  @Nullable
  @Override
  public LoadData<InputStream> buildLoadData(ContactPhotoUri contactPhotoUri, int width, int height, Options options) {
    return new LoadData<>(contactPhotoUri, new StreamLocalUriFetcher(context.getContentResolver(), contactPhotoUri.uri));
  }

  @Override
  public boolean handles(ContactPhotoUri contactPhotoUri) {
    return true;
  }

  static class Factory implements ModelLoaderFactory<ContactPhotoUri, InputStream> {

    private final Context context;

    Factory(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public ModelLoader<ContactPhotoUri, InputStream> build(MultiModelLoaderFactory multiFactory) {
      return new ContactPhotoUriLoader(context);
    }

    @Override
    public void teardown() {
      // Do nothing.
    }
  }

  public static class ContactPhotoUri implements Key {
    public @NonNull Uri uri;

    public ContactPhotoUri(@NonNull Uri uri) {
      this.uri = uri;
    }

    @Override
    public void updateDiskCacheKey(MessageDigest messageDigest) {
      messageDigest.update(uri.toString().getBytes());
    }

    @Override
    public boolean equals(Object other) {
      if (other == null || !(other instanceof ContactPhotoUri)) return false;

      return this.uri.equals(((ContactPhotoUri)other).uri);
    }

    public int hashCode() {
      return uri.hashCode();
    }
  }
}

