package org.thoughtcrime.securesms.glide;

import android.content.Context;
import android.support.annotation.Nullable;

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

  @Nullable
  @Override
  public LoadData<InputStream> buildLoadData(ContactPhoto contactPhoto, int width, int height, Options options) {
    return new LoadData<>(contactPhoto, new ContactPhotoFetcher(context, contactPhoto));
  }

  @Override
  public boolean handles(ContactPhoto contactPhoto) {
    return true;
  }

  public static class Factory implements ModelLoaderFactory<ContactPhoto, InputStream> {

    private final Context context;

    public Factory(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public ModelLoader<ContactPhoto, InputStream> build(MultiModelLoaderFactory multiFactory) {
      return new ContactPhotoLoader(context);
    }

    @Override
    public void teardown() {}
  }
}