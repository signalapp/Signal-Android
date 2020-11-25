package org.thoughtcrime.securesms.giph.net;


import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class GiphyGifLoader extends GiphyLoader {

  public GiphyGifLoader(@NonNull Context context, @Nullable String searchString) {
    super(context, searchString);
  }

  @Override
  protected String getTrendingUrl() {
    return "https://api.giphy.com/v1/gifs/trending?api_key=3o6ZsYH6U6Eri53TXy&offset=%d&limit=" + PAGE_SIZE;
  }

  @Override
  protected String getSearchUrl() {
    return "https://api.giphy.com/v1/gifs/search?api_key=3o6ZsYH6U6Eri53TXy&offset=%d&limit=" + PAGE_SIZE + "&q=%s";
  }
}
