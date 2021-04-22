package org.thoughtcrime.securesms.giph.mp4;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.logging.Log;
import org.signal.paging.PagedDataSource;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.giph.model.GiphyImage;
import org.thoughtcrime.securesms.giph.model.GiphyResponse;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.whispersystems.util.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Data source for GiphyImages.
 */
final class GiphyMp4PagedDataSource implements PagedDataSource<GiphyImage> {

  private static final String TAG = Log.tag(GiphyMp4PagedDataSource.class);

  private final String       searchString;
  private final OkHttpClient client;

  GiphyMp4PagedDataSource(@Nullable String searchQuery) {
    this.searchString = searchQuery;
    this.client       = ApplicationDependencies.getOkHttpClient();
  }

  @Override
  public int size() {
    try {
      GiphyResponse response = performFetch(0, 1);

      return response.getPagination().getTotalCount();
    } catch (IOException | NullPointerException e) {
      Log.w(TAG, "Failed to get size", e);
      return 0;
    }
  }

  @Override
  public @NonNull List<GiphyImage> load(int start, int length, @NonNull CancellationSignal cancellationSignal) {
    try {
      Log.d(TAG, "Loading from " + start + " to " + (start + length));
      return new LinkedList<>(performFetch(start, length).getData());
    } catch (IOException | NullPointerException e) {
      Log.w(TAG, "Failed to load content", e);
      return new LinkedList<>();
    }
  }

  private @NonNull GiphyResponse performFetch(int start, int length) throws IOException {
    String url;

    if (TextUtils.isEmpty(searchString)) url = getTrendingUrl(start, length);
    else                                 url = getSearchUrl(start, length, Uri.encode(searchString));

    Request request = new Request.Builder().url(url).build();

    try (Response response = client.newCall(request).execute()) {

      if (!response.isSuccessful()) {
        throw new IOException("Unexpected code " + response);
      }

      if (response.body() == null) {
        throw new IOException("Response body was not present");
      }

      return JsonUtils.fromJson(response.body().byteStream(), GiphyResponse.class);
    }
  }

  private String getTrendingUrl(int start, int length) {
    return "https://api.giphy.com/v1/gifs/trending?api_key=3o6ZsYH6U6Eri53TXy&offset=" + start + "&limit=" + length;
  }

  private String getSearchUrl(int start, int length, @NonNull String query) {
    return "https://api.giphy.com/v1/gifs/search?api_key=3o6ZsYH6U6Eri53TXy&offset=" + start + "&limit=" + length + "&q=" + Uri.encode(query);
  }
}
