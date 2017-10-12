package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;

import java.io.InputStream;
import java.security.MessageDigest;

public class DecryptableStreamUriLoader implements ModelLoader<DecryptableUri, InputStream> {

  private final Context context;

  private DecryptableStreamUriLoader(Context context) {
    this.context = context;
  }

  @Nullable
  @Override
  public LoadData<InputStream> buildLoadData(DecryptableUri decryptableUri, int width, int height, Options options) {
    return new LoadData<>(decryptableUri, new DecryptableStreamLocalUriFetcher(context, decryptableUri.masterSecret, decryptableUri.uri));
  }

  @Override
  public boolean handles(DecryptableUri decryptableUri) {
    return true;
  }

  static class Factory implements ModelLoaderFactory<DecryptableUri, InputStream> {

    private final Context context;

    Factory(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public ModelLoader<DecryptableUri, InputStream> build(MultiModelLoaderFactory multiFactory) {
      return new DecryptableStreamUriLoader(context);
    }

    @Override
    public void teardown() {
      // Do nothing.
    }
  }

  public static class DecryptableUri implements Key {
    public @NonNull MasterSecret masterSecret;
    public @NonNull Uri          uri;

    public DecryptableUri(@NonNull MasterSecret masterSecret, @NonNull Uri uri) {
      this.masterSecret = masterSecret;
      this.uri          = uri;
    }

    @Override
    public void updateDiskCacheKey(MessageDigest messageDigest) {
      messageDigest.update(uri.toString().getBytes());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      DecryptableUri that = (DecryptableUri)o;

      return uri.equals(that.uri);

    }

    @Override
    public int hashCode() {
      return uri.hashCode();
    }
  }
}

