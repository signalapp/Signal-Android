package org.thoughtcrime.securesms.glide;

import android.support.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import org.thoughtcrime.securesms.giph.model.GiphyPaddedUrl;
import org.thoughtcrime.securesms.giph.net.GiphyProxySelector;

import java.io.InputStream;

import okhttp3.OkHttpClient;

public class GiphyPaddedUrlLoader implements ModelLoader<GiphyPaddedUrl, InputStream> {

  private final OkHttpClient client;

  private GiphyPaddedUrlLoader(OkHttpClient client) {
    this.client  = client;
  }

  @Nullable
  @Override
  public LoadData<InputStream> buildLoadData(GiphyPaddedUrl url, int width, int height, Options options) {
    return new LoadData<>(url, new GiphyPaddedUrlFetcher(client, url));
  }

  @Override
  public boolean handles(GiphyPaddedUrl url) {
    return true;
  }

  public static class Factory implements ModelLoaderFactory<GiphyPaddedUrl, InputStream> {

    private final OkHttpClient client;

    public Factory() {
      this.client  = new OkHttpClient.Builder().proxySelector(new GiphyProxySelector()).build();
    }

    @Override
    public ModelLoader<GiphyPaddedUrl, InputStream> build(MultiModelLoaderFactory multiFactory) {
      return new GiphyPaddedUrlLoader(client);
    }

    @Override
    public void teardown() {}
  }
}