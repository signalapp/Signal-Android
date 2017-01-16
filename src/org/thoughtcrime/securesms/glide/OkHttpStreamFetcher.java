package org.thoughtcrime.securesms.glide;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.util.ContentLengthInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Fetches an {@link InputStream} using the okhttp library.
 */
public class OkHttpStreamFetcher implements DataFetcher<InputStream> {

  private static final String TAG = OkHttpStreamFetcher.class.getName();

  private final OkHttpClient client;
  private final GlideUrl     url;
  private       InputStream  stream;
  private       ResponseBody responseBody;

  public OkHttpStreamFetcher(OkHttpClient client, GlideUrl url) {
    this.client = client;
    this.url = url;
  }

  @Override
  public InputStream loadData(Priority priority) throws Exception {
    Request.Builder requestBuilder = new Request.Builder()
        .url(url.toStringUrl());

    for (Map.Entry<String, String> headerEntry : url.getHeaders().entrySet()) {
      String key = headerEntry.getKey();
      requestBuilder.addHeader(key, headerEntry.getValue());
    }

    Request  request  = requestBuilder.build();
    Response response = client.newCall(request).execute();

    responseBody = response.body();

    if (!response.isSuccessful()) {
      throw new IOException("Request failed with code: " + response.code());
    }

    long contentLength = responseBody.contentLength();
    stream = ContentLengthInputStream.obtain(responseBody.byteStream(), contentLength);
    return stream;
  }

  @Override
  public void cleanup() {
    if (stream != null) {
      try {
        stream.close();
      } catch (IOException e) {
        // Ignored
      }
    }
    if (responseBody != null) {
      responseBody.close();
    }
  }

  @Override
  public String getId() {
    return url.getCacheKey();
  }

  @Override
  public void cancel() {
    // TODO: call cancel on the client when this method is called on a background thread. See #257
  }
}