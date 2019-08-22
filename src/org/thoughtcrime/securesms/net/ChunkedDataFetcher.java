package org.thoughtcrime.securesms.net;

import androidx.annotation.NonNull;
import android.text.TextUtils;

import com.annimon.stream.Stream;
import com.bumptech.glide.util.ContentLengthInputStream;

import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ChunkedDataFetcher {

  private static final String TAG = ChunkedDataFetcher.class.getSimpleName();

  private static final CacheControl NO_CACHE = new CacheControl.Builder().noCache().build();

  private static final long MB = 1024 * 1024;
  private static final long KB = 1024;

  private final OkHttpClient client;

  public ChunkedDataFetcher(@NonNull OkHttpClient client) {
    this.client = client;
  }

  public RequestController fetch(@NonNull String url, long contentLength, @NonNull Callback callback) {
    if (contentLength <= 0) {
      return fetchChunksWithUnknownTotalSize(url, callback);
    }

    CompositeRequestController compositeController = new CompositeRequestController();
    fetchChunks(url, contentLength, Optional.absent(), compositeController, callback);
    return compositeController;
  }

  private RequestController fetchChunksWithUnknownTotalSize(@NonNull String url, @NonNull Callback callback) {
    CompositeRequestController compositeController = new CompositeRequestController();

    long    chunkSize = new SecureRandom().nextInt(1024) + 1024;
    Request request   = new Request.Builder()
                                   .url(url)
                                   .cacheControl(NO_CACHE)
                                   .addHeader("Range", "bytes=0-" + (chunkSize - 1))
                                   .addHeader("Accept-Encoding", "identity")
                                   .build();

    Call firstChunkCall = client.newCall(request);
    compositeController.addController(new CallRequestController(firstChunkCall));

    firstChunkCall.enqueue(new okhttp3.Callback() {
      @Override
      public void onFailure(@NonNull Call call, @NonNull IOException e) {
        if (!compositeController.isCanceled()) {
          callback.onFailure(e);
          compositeController.cancel();
        }
      }

      @Override
      public void onResponse(@NonNull Call call, @NonNull Response response) {
        String contentRange = response.header("Content-Range");

        if (!response.isSuccessful()) {
          Log.w(TAG, "Non-successful response code: " + response.code());
          callback.onFailure(new IOException("Non-successful response code: " + response.code()));
          compositeController.cancel();
          if (response.body() != null) response.body().close();
          return;
        }

        if (TextUtils.isEmpty(contentRange)) {
          Log.w(TAG, "Missing Content-Range header.");
          callback.onFailure(new IOException("Missing Content-Length header."));
          compositeController.cancel();
          if (response.body() != null) response.body().close();
          return;
        }

        if (response.body() == null) {
          Log.w(TAG, "Missing body.");
          callback.onFailure(new IOException("Missing body on initial request."));
          compositeController.cancel();
          return;
        }

        Optional<Long> contentLength = parseLengthFromContentRange(contentRange);

        if (!contentLength.isPresent()) {
          Log.w(TAG, "Unable to parse length from Content-Range.");
          callback.onFailure(new IOException("Unable to get parse length from Content-Range."));
          compositeController.cancel();
          return;
        }

        if (chunkSize >= contentLength.get()) {
          try {
            callback.onSuccess(response.body().byteStream());
          } catch (IOException e) {
            callback.onFailure(e);
            compositeController.cancel();
          }
        } else {
          InputStream stream = ContentLengthInputStream.obtain(response.body().byteStream(), chunkSize);
          fetchChunks(url, contentLength.get(), Optional.of(new Pair<>(stream, chunkSize)), compositeController, callback);
        }
      }
    });

    return compositeController;
  }

  private void fetchChunks(@NonNull String url,
                           long contentLength,
                           Optional<Pair<InputStream, Long>> firstChunk,
                           CompositeRequestController compositeController,
                           Callback callback)
  {
    List<ByteRange> requestPattern;
    try {
      if (firstChunk.isPresent()) {
        requestPattern = Stream.of(getRequestPattern(contentLength - firstChunk.get().second()))
                               .map(b -> new ByteRange(b.start + firstChunk.get().second(),
                                                       b.end   + firstChunk.get().second(),
                                                       b.ignoreFirst))
                               .toList();
      } else {
        requestPattern = getRequestPattern(contentLength);
      }
    } catch (IOException e) {
      callback.onFailure(e);
      compositeController.cancel();
      return;
    }

    SignalExecutors.UNBOUNDED.execute(() -> {
      List<CallRequestController> controllers = Stream.of(requestPattern).map(range -> makeChunkRequest(client, url, range)).toList();
      List<InputStream>           streams     = new ArrayList<>(controllers.size() + (firstChunk.isPresent() ? 1 : 0));

      if (firstChunk.isPresent()) {
        streams.add(firstChunk.get().first());
      }

      Stream.of(controllers).forEach(compositeController::addController);

      for (CallRequestController controller : controllers) {
        Optional<InputStream> stream = controller.getStream();

        if (!stream.isPresent()) {
          Log.w(TAG, "Stream was canceled.");
          callback.onFailure(new IOException("Failure"));
          compositeController.cancel();
          return;
        }

        streams.add(stream.get());
      }

      try {
        callback.onSuccess(new InputStreamList(streams));
      } catch (IOException e) {
        callback.onFailure(e);
        compositeController.cancel();
      }
    });
  }

  private CallRequestController makeChunkRequest(@NonNull OkHttpClient client, @NonNull String url, @NonNull ByteRange range) {
    Request request = new Request.Builder()
                                 .url(url)
                                 .cacheControl(NO_CACHE)
                                 .addHeader("Range", "bytes=" + range.start + "-" + range.end)
                                 .addHeader("Accept-Encoding", "identity")
                                 .build();

    Call                  call           = client.newCall(request);
    CallRequestController callController = new CallRequestController(call);

    call.enqueue(new okhttp3.Callback() {
      @Override
      public void onFailure(@NonNull Call call, @NonNull IOException e) {
        callController.cancel();
      }

      @Override
      public void onResponse(@NonNull Call call, @NonNull Response response) {
        if (!response.isSuccessful()) {
          callController.cancel();
          if (response.body() != null) response.body().close();
          return;
        }

        if (response.body() == null) {
          callController.cancel();
          if (response.body() != null) response.body().close();
          return;
        }

        InputStream stream = new SkippingInputStream(ContentLengthInputStream.obtain(response.body().byteStream(), response.body().contentLength()), range.ignoreFirst);
        callController.setStream(stream);
      }
    });

    return callController;
  }

  private Optional<Long> parseLengthFromContentRange(@NonNull String contentRange) {
    int totalStartPos = contentRange.indexOf('/');

    if (totalStartPos >= 0 && contentRange.length() > totalStartPos + 1) {
      String totalString = contentRange.substring(totalStartPos + 1);

      try {
        return Optional.of(Long.parseLong(totalString));
      } catch (NumberFormatException e) {
        return Optional.absent();
      }
    }

    return Optional.absent();
  }

  private List<ByteRange> getRequestPattern(long size) throws IOException {
    if      (size > MB)       return getRequestPattern(size, MB);
    else if (size > 500 * KB) return getRequestPattern(size, 500 * KB);
    else if (size > 100 * KB) return getRequestPattern(size, 100 * KB);
    else if (size > 50 * KB)  return getRequestPattern(size, 50 * KB);
    else if (size > 10 * KB)  return getRequestPattern(size, 10 * KB);
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

  public interface Callback {
    void onSuccess(InputStream stream) throws IOException;
    void onFailure(Exception e);
  }
}
