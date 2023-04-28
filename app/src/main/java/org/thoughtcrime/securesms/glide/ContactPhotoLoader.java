package org.thoughtcrime.securesms.glide;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;

import java.io.InputStream;

public class ContactPhotoLoader implements ModelLoader<ContactPhoto, InputStream> {

  private final Context context;

  private ContactPhotoLoader(Context context) {
    this.context = context;
  }

  @Override
  public @Nullable LoadData<InputStream> buildLoadData(@NonNull ContactPhoto contactPhoto, int width, int height, @NonNull Options options) {
    return new LoadData<>(contactPhoto, new ContactPhotoFetcher(context, contactPhoto));
  }

  @Override
  public boolean handles(@NonNull ContactPhoto contactPhoto) {
    return true;
  }

  public static class Factory implements ModelLoaderFactory<ContactPhoto, InputStream> {

    private final Context context;

    public Factory(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public @NonNull ModelLoader<ContactPhoto, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
      return new ContactPhotoLoader(context);
    }

    @Override
    public void teardown() {}
  }
}
