package org.thoughtcrime.securesms.giph.net;


import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import org.thoughtcrime.securesms.logging.Log;


import org.thoughtcrime.securesms.giph.model.GiphyImage;
import org.thoughtcrime.securesms.giph.model.GiphyResponse;
import org.thoughtcrime.securesms.util.AsyncLoader;
import org.thoughtcrime.securesms.util.JsonUtils;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public abstract class GiphyLoader extends AsyncLoader<List<GiphyImage>> {

  private static final String TAG = GiphyLoader.class.getName();

  public static int PAGE_SIZE = 100;

  @Nullable private String searchString;

  private final OkHttpClient client;

  protected GiphyLoader(@NonNull Context context, @Nullable String searchString) {
    super(context);
    this.searchString = searchString;
    this.client       = new OkHttpClient.Builder().proxySelector(new GiphyProxySelector()).build();
  }

  @Override
  public List<GiphyImage> loadInBackground() {
    return loadPage(0);
  }

  public @NonNull List<GiphyImage> loadPage(int offset) {
    try {
      String url;

      if (TextUtils.isEmpty(searchString)) url = String.format(getTrendingUrl(), offset);
      else                                 url = String.format(getSearchUrl(), offset, Uri.encode(searchString));

      Request  request  = new Request.Builder().url(url).build();
      Response response = client.newCall(request).execute();

      if (!response.isSuccessful()) {
        throw new IOException("Unexpected code " + response);
      }

      GiphyResponse    giphyResponse = JsonUtils.fromJson(response.body().byteStream(), GiphyResponse.class);
      List<GiphyImage> results       = giphyResponse.getData();

      if (results == null) return new LinkedList<>();
      else                 return results;

    } catch (IOException e) {
      Log.w(TAG, e);
      return new LinkedList<>();
    }
  }

  protected abstract String getTrendingUrl();
  protected abstract String getSearchUrl();
}
