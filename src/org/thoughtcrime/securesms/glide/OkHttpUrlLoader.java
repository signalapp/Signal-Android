package org.thoughtcrime.securesms.glide;

import android.content.Context;

import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.GenericLoaderFactory;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.squareup.okhttp.OkHttpClient;

import org.thoughtcrime.securesms.giph.net.GiphyProxySelector;

import java.io.InputStream;

/**
 * A simple model loader for fetching media over http/https using OkHttp.
 */
public class OkHttpUrlLoader implements ModelLoader<GlideUrl, InputStream> {

  /**
   * The default factory for {@link OkHttpUrlLoader}s.
   */
  public static class Factory implements ModelLoaderFactory<GlideUrl, InputStream> {
    private static volatile OkHttpClient internalClient;
    private OkHttpClient client;

    private static OkHttpClient getInternalClient() {
      if (internalClient == null) {
        synchronized (Factory.class) {
          if (internalClient == null) {
            internalClient = new OkHttpClient();
            internalClient.setProxySelector(new GiphyProxySelector());
          }
        }
      }
      return internalClient;
    }

    /**
     * Constructor for a new Factory that runs requests using a static singleton client.
     */
    public Factory() {
      this(getInternalClient());
    }

    /**
     * Constructor for a new Factory that runs requests using given client.
     */
    private Factory(OkHttpClient client) {
      this.client = client;
    }

    @Override
    public ModelLoader<GlideUrl, InputStream> build(Context context, GenericLoaderFactory factories) {
      return new OkHttpUrlLoader(client);
    }

    @Override
    public void teardown() {
      // Do nothing, this instance doesn't own the client.
    }
  }

  private final OkHttpClient client;

  private OkHttpUrlLoader(OkHttpClient client) {
    this.client = client;
  }

  @Override
  public DataFetcher<InputStream> getResourceFetcher(GlideUrl model, int width, int height) {
    return new OkHttpStreamFetcher(client, model);
  }
}