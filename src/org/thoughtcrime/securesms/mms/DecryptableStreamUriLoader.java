package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.net.Uri;
import android.util.Pair;

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
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.EncryptedUriModel;

import java.io.InputStream;

/**
 * A {@link ModelLoader} for translating uri models into {@link InputStream} data. Capable of handling 'http',
 * 'https', 'android.resource', 'content', and 'file' schemes. Unsupported schemes will throw an exception in
 * {@link #getResourceFetcher(Uri, int, int)}.
 */
public class DecryptableStreamUriLoader implements StreamModelLoader<EncryptedUriModel> {
  private final Context context;

  /**
   * THe default factory for {@link com.bumptech.glide.load.model.stream.StreamUriLoader}s.
   */
  public static class Factory implements ModelLoaderFactory<EncryptedUriModel, InputStream> {

    @Override
    public StreamModelLoader<EncryptedUriModel> build(Context context, GenericLoaderFactory factories) {
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
  public DataFetcher<InputStream> getResourceFetcher(EncryptedUriModel model, int width, int height) {
    return new DecryptableStreamLocalUriFetcher(context, model.masterSecret, model.uri);
  }

  public static class EncryptedUriModel {
    public MasterSecret masterSecret;
    public Uri          uri;

    public EncryptedUriModel(MasterSecret masterSecret, Uri uri) {
      this.masterSecret = masterSecret;
      this.uri          = uri;
    }
  }
}

