package org.thoughtcrime.securesms.glide;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import org.thoughtcrime.securesms.mms.DecryptableUri;

import java.io.InputStream;

public class DecryptableStreamUriLoader implements ModelLoader<DecryptableUri, InputStream> {

  private final Context context;

  private DecryptableStreamUriLoader(Context context) {
    this.context = context;
  }

  @Nullable
  @Override
  public LoadData<InputStream> buildLoadData(@NonNull DecryptableUri decryptableUri, int width, int height, @NonNull Options options) {
    return new LoadData<>(decryptableUri, new DecryptableStreamLocalUriFetcher(context, decryptableUri.getUri()));
  }

  @Override
  public boolean handles(@NonNull DecryptableUri decryptableUri) {
    return true;
  }

  public static class Factory implements ModelLoaderFactory<DecryptableUri, InputStream> {

    private final Context context;

    public Factory(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public @NonNull ModelLoader<DecryptableUri, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
      return new DecryptableStreamUriLoader(context);
    }

    @Override
    public void teardown() {
      // Do nothing.
    }
  }
}

