package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.DiskCache;
import com.bumptech.glide.load.engine.cache.DiskCacheAdapter;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;

import org.thoughtcrime.securesms.contacts.avatars.ContactPhoto;
import org.thoughtcrime.securesms.giph.model.GiphyPaddedUrl;
import org.thoughtcrime.securesms.glide.ContactPhotoLoader;
import org.thoughtcrime.securesms.glide.GiphyPaddedUrlLoader;
import org.thoughtcrime.securesms.glide.OkHttpUrlLoader;
import org.thoughtcrime.securesms.mms.AttachmentStreamUriLoader.AttachmentModel;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;

import java.io.InputStream;

@GlideModule
public class SignalGlideModule extends AppGlideModule {

  @Override
  public boolean isManifestParsingEnabled() {
    return false;
  }

  @Override
  public void applyOptions(Context context, GlideBuilder builder) {
    builder.setLogLevel(Log.ERROR);
//    builder.setDiskCache(new NoopDiskCacheFactory());
  }

  @Override
  public void registerComponents(Context context, Glide glide, Registry registry) {
    registry.append(ContactPhoto.class, InputStream.class, new ContactPhotoLoader.Factory(context));
    registry.append(DecryptableUri.class, InputStream.class, new DecryptableStreamUriLoader.Factory(context));
    registry.append(AttachmentModel.class, InputStream.class, new AttachmentStreamUriLoader.Factory());
    registry.append(GiphyPaddedUrl.class, InputStream.class, new GiphyPaddedUrlLoader.Factory());
    registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory());
  }

  public static class NoopDiskCacheFactory implements DiskCache.Factory {
    @Override
    public DiskCache build() {
      return new DiskCacheAdapter();
    }
  }
}
