package org.privatechats.securesms.mms;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.GenericLoaderFactory;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.stream.StreamModelLoader;

import org.privatechats.securesms.crypto.MasterSecret;
import org.privatechats.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;

import java.io.InputStream;

/**
 * A {@link ModelLoader} for translating uri models into {@link InputStream} data. Capable of handling 'http',
 * 'https', 'android.resource', 'content', and 'file' schemes. Unsupported schemes will throw an exception in
 * {@link #getResourceFetcher(Uri, int, int)}.
 */
public class DecryptableStreamUriLoader implements StreamModelLoader<DecryptableUri> {
  private final Context context;

  /**
   * THe default factory for {@link com.bumptech.glide.load.model.stream.StreamUriLoader}s.
   */
  public static class Factory implements ModelLoaderFactory<DecryptableUri, InputStream> {

    @Override
    public StreamModelLoader<DecryptableUri> build(Context context, GenericLoaderFactory factories) {
      return new DecryptableStreamUriLoader(context);
    }

    @Override
    public void teardown() {
      // Do nothing.
    }
  }

  public DecryptableStreamUriLoader(Context context) {
    this.context = context;
  }

  @Override
  public DataFetcher<InputStream> getResourceFetcher(DecryptableUri model, int width, int height) {
    return new DecryptableStreamLocalUriFetcher(context, model.masterSecret, model.uri);
  }

  public static class DecryptableUri {
    public @NonNull MasterSecret masterSecret;
    public @NonNull Uri          uri;

    public DecryptableUri(@NonNull MasterSecret masterSecret, @NonNull Uri uri) {
      this.masterSecret = masterSecret;
      this.uri          = uri;
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

