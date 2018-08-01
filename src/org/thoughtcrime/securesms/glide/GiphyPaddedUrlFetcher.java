package org.thoughtcrime.securesms.glide;


import android.support.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.util.ContentLengthInputStream;

import org.thoughtcrime.securesms.giph.model.GiphyPaddedUrl;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

class GiphyPaddedUrlFetcher implements DataFetcher<InputStream> {

  private static final String TAG = GiphyPaddedUrlFetcher.class.getSimpleName();

  private static final long MB = 1024 * 1024;
  private static final long KB = 1024;

  private final OkHttpClient   client;
  private final GiphyPaddedUrl url;

  private List<ResponseBody> bodies;
  private List<InputStream>  rangeStreams;
  private InputStream        stream;

  GiphyPaddedUrlFetcher(@NonNull OkHttpClient client,
                        @NonNull GiphyPaddedUrl url)
  {
    this.client  = client;
    this.url     = url;
  }

  @Override
  public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super InputStream> callback) {
    bodies       = new LinkedList<>();
    rangeStreams = new LinkedList<>();
    stream       = null;

    try {
      List<ByteRange> requestPattern = getRequestPattern(url.getSize());

      for (ByteRange range : requestPattern) {
        Request request = new Request.Builder()
                                     .addHeader("Range", "bytes=" + range.start + "-" + range.end)
                                     .addHeader("Accept-Encoding", "identity")
                                     .url(url.getTarget())
                                     .get()
                                     .build();

        Response response = client.newCall(request).execute();

        if (!response.isSuccessful()) {
          throw new IOException("Bad response: " + response.code() + " - " + response.message());
        }

        ResponseBody responseBody = response.body();

        if (responseBody == null) throw new IOException("Response body was null");
        else                      bodies.add(responseBody);

        rangeStreams.add(new SkippingInputStream(ContentLengthInputStream.obtain(responseBody.byteStream(), responseBody.contentLength()), range.ignoreFirst));
      }

      stream = new InputStreamList(rangeStreams);
      callback.onDataReady(stream);
    } catch (IOException e) {
      Log.w(TAG, e);
      callback.onLoadFailed(e);
    }
  }

  @Override
  public void cleanup() {
    if (rangeStreams != null) {
      for (InputStream rangeStream : rangeStreams) {
        try {
          if (rangeStream != null) rangeStream.close();
        } catch (IOException ignored) {}
      }
    }

    if (bodies != null) {
      for (ResponseBody body : bodies) {
        if (body != null) body.close();
      }
    }

    if (stream != null) {
      try {
        stream.close();
      } catch (IOException ignored) {}
    }
  }

  @Override
  public void cancel() {

  }

  @NonNull
  @Override
  public Class<InputStream> getDataClass() {
    return InputStream.class;
  }

  @NonNull
  @Override
  public DataSource getDataSource() {
    return DataSource.REMOTE;
  }

  private List<ByteRange> getRequestPattern(long size) throws IOException {
    if      (size > MB)       return getRequestPattern(size, MB);
    else if (size > 500 * KB) return getRequestPattern(size, 500 * KB);
    else if (size > 100 * KB) return getRequestPattern(size, 100 * KB);
    else if (size > 50 * KB)  return getRequestPattern(size, 50 * KB);
    else if (size > KB)       return getRequestPattern(size, KB);

    throw new IOException("Unsupported size: " + size);
  }

  private List<ByteRange> getRequestPattern(long size, long increment) {
    List<ByteRange> results = new LinkedList<>();

    long offset = 0;

    while (size - offset > increment) {
      results.add(new ByteRange(offset, offset + increment - 1, 0));
      offset += increment;
    }

    if (size - offset > 0) {
      results.add(new ByteRange(size - increment, size-1, increment - (size - offset)));
    }

    return results;
  }

  private static class ByteRange {
    private final long start;
    private final long end;
    private final long ignoreFirst;

    private ByteRange(long start, long end, long ignoreFirst) {
      this.start       = start;
      this.end         = end;
      this.ignoreFirst = ignoreFirst;
    }
  }

  private static class SkippingInputStream extends FilterInputStream {

    private long skip;

    SkippingInputStream(InputStream in, long skip) {
      super(in);
      this.skip = skip;
    }

    @Override
    public int read() throws IOException {
      if (skip != 0) {
        skipFully(skip);
        skip = 0;
      }

      return super.read();
    }

    @Override
    public int read(@NonNull byte[] buffer) throws IOException {
      if (skip != 0) {
        skipFully(skip);
        skip = 0;
      }

      return super.read(buffer);
    }

    @Override
    public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
      if (skip != 0) {
        skipFully(skip);
        skip = 0;
      }

      return super.read(buffer, offset, length);
    }

    @Override
    public int available() throws IOException {
      return Util.toIntExact(super.available() - skip);
    }

    private void skipFully(long amount) throws IOException {
      byte[] buffer = new byte[4096];

      while (amount > 0) {
        int read = super.read(buffer, 0, Math.min(buffer.length, Util.toIntExact(amount)));

        if (read != -1) amount -= read;
        else            return;
      }
    }
  }

  private static class InputStreamList extends InputStream {

    private final List<InputStream> inputStreams;

    private int currentStreamIndex = 0;

    InputStreamList(List<InputStream> inputStreams) {
      this.inputStreams = inputStreams;
    }

    @Override
    public int read() throws IOException {
      while (currentStreamIndex < inputStreams.size()) {
        int result = inputStreams.get(currentStreamIndex).read();

        if (result == -1) currentStreamIndex++;
        else              return result;
      }

      return -1;
    }

    @Override
    public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
      while (currentStreamIndex < inputStreams.size()) {
        int result = inputStreams.get(currentStreamIndex).read(buffer, offset, length);

        if (result == -1) currentStreamIndex++;
        else              return result;
      }

      return -1;
    }

    @Override
    public int read(@NonNull byte[] buffer) throws IOException {
      return read(buffer, 0, buffer.length);
    }

    @Override
    public void close() throws IOException {
      for (InputStream stream : inputStreams) {
        try {
          stream.close();
        } catch (IOException ignored) {}
      }
    }

    @Override
    public int available() {
      int total = 0;

      for (int i=currentStreamIndex;i<inputStreams.size();i++) {
        try {
          int available = inputStreams.get(i).available();

          if (available != -1) total += available;
        } catch (IOException ignored) {}
      }

      return total;
    }
  }

}
