package org.thoughtcrime.securesms.glide;

import android.support.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import org.thoughtcrime.securesms.giph.model.ChunkedImageUrl;
import org.thoughtcrime.securesms.net.ContentProxySelector;

import java.io.InputStream;

import okhttp3.OkHttpClient;

public class ChunkedImageUrlLoader implements ModelLoader<ChunkedImageUrl, InputStream> {

  private final OkHttpClient client;

  private ChunkedImageUrlLoader(OkHttpClient client) {
    this.client  = client;
  }

  @Nullable
  @Override
  public LoadData<InputStream> buildLoadData(ChunkedImageUrl url, int width, int height, Options options) {
    return new LoadData<>(url, new ChunkedImageUrlFetcher(client, url));
  }

  @Override
  public boolean handles(ChunkedImageUrl url) {
    return true;
  }

  public static class Factory implements ModelLoaderFactory<ChunkedImageUrl, InputStream> {

    private final OkHttpClient client;

    public Factory() {
      this.client  = new OkHttpClient.Builder()
                                     .proxySelector(new ContentProxySelector())
                                     .cache(null)
                                     .build();
    }

    @Override
    public ModelLoader<ChunkedImageUrl, InputStream> build(MultiModelLoaderFactory multiFactory) {
      return new ChunkedImageUrlLoader(client);
    }

    @Override
    public void teardown() {}
  }
}