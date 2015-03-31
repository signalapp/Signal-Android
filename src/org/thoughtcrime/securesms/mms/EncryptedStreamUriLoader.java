package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.net.Uri;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.data.StreamAssetPathFetcher;
import com.bumptech.glide.load.model.GenericLoaderFactory;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.UriLoader;
import com.bumptech.glide.load.model.stream.StreamModelLoader;

import org.thoughtcrime.securesms.crypto.MasterSecret;

import java.io.InputStream;

/**
 * A {@link ModelLoader} for translating uri models into {@link InputStream} data. Capable of handling 'http',
 * 'https', 'android.resource', 'content', and 'file' schemes. Unsupported schemes will throw an exception in
 * {@link #getResourceFetcher(Uri, int, int)}.
 */
public class EncryptedStreamUriLoader extends UriLoader<InputStream> implements StreamModelLoader<Uri> {
  private MasterSecret masterSecret;

  /**
   * THe default factory for {@link com.bumptech.glide.load.model.stream.StreamUriLoader}s.
   */
  public static class Factory implements ModelLoaderFactory<Uri, InputStream> {
    private MasterSecret masterSecret;

    public Factory(MasterSecret masterSecret) {
      this.masterSecret = masterSecret;
    }

    @Override
    public ModelLoader<Uri, InputStream> build(Context context, GenericLoaderFactory factories) {
      return new EncryptedStreamUriLoader(context, masterSecret, factories.buildModelLoader(GlideUrl.class, InputStream.class));
    }

    @Override
    public void teardown() {
      // Do nothing.
    }
  }

  public EncryptedStreamUriLoader(Context context, MasterSecret masterSecret) {
    this(context, masterSecret, Glide.buildStreamModelLoader(GlideUrl.class, context));
  }

  public EncryptedStreamUriLoader(Context context, MasterSecret masterSecret, ModelLoader<GlideUrl, InputStream> urlLoader) {
    super(context, urlLoader);
    this.masterSecret = masterSecret;
  }

  @Override
  protected DataFetcher<InputStream> getLocalUriFetcher(Context context, Uri uri) {
    return new EncryptedStreamLocalUriFetcher(context, masterSecret, uri);
  }

  @Override
  protected DataFetcher<InputStream> getAssetPathFetcher(Context context, String assetPath) {
    return new StreamAssetPathFetcher(context.getApplicationContext().getAssets(), assetPath);
  }
}

