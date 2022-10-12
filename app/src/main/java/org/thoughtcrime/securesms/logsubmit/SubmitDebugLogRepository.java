package org.thoughtcrime.securesms.logsubmit;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import org.json.JSONException;
import org.json.JSONObject;
import org.signal.core.util.StreamUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.signal.core.util.logging.Scrubber;
import org.signal.core.util.tracing.Tracer;
import org.thoughtcrime.securesms.database.LogDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.net.StandardUserAgentInterceptor;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.signal.core.util.Stopwatch;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

/**
 * Handles retrieving, scrubbing, and uploading of all debug logs.
 *
 * Adding a new log section:
 * - Create a new {@link LogSection}.
 * - Add it to {@link #SECTIONS}. The order of the list is the order the sections are displayed.
 */
public class SubmitDebugLogRepository {

  private static final String TAG = Log.tag(SubmitDebugLogRepository.class);

  private static final char   TITLE_DECORATION = '=';
  private static final int    MIN_DECORATIONS  = 5;
  private static final int    SECTION_SPACING  = 3;
  private static final String API_ENDPOINT     = "https://debuglogs.org";

  /** Ordered list of log sections. */
  private static final List<LogSection> SECTIONS = new ArrayList<LogSection>() {{
    add(new LogSectionSystemInfo());
    add(new LogSectionJobs());
    add(new LogSectionConstraints());
    add(new LogSectionCapabilities());
    add(new LogSectionLocalMetrics());
    add(new LogSectionFeatureFlags());
    add(new LogSectionPin());
    if (Build.VERSION.SDK_INT >= 28) {
      add(new LogSectionPower());
    }
    add(new LogSectionNotifications());
    add(new LogSectionNotificationProfiles());
    add(new LogSectionExoPlayerPool());
    add(new LogSectionKeyPreferences());
    add(new LogSectionSMS());
    add(new LogSectionStories());
    add(new LogSectionBadges());
    add(new LogSectionPermissions());
    add(new LogSectionTrace());
    add(new LogSectionThreads());
    add(new LogSectionThreadDump());
    if (FeatureFlags.internalUser()) {
      add(new LogSectionSenderKey());
    }
    add(new LogSectionRemappedRecords());
    add(new LogSectionLogcat());
    add(new LogSectionLoggerHeader());
  }};

  private final Application     context;
  private final ExecutorService executor;

  public SubmitDebugLogRepository() {
    this.context  = ApplicationDependencies.getApplication();
    this.executor = SignalExecutors.SERIAL;
  }

  public void getPrefixLogLines(@NonNull Callback<List<LogLine>> callback) {
    executor.execute(() -> callback.onResult(getPrefixLogLinesInternal()));
  }

  public void buildAndSubmitLog(@NonNull Callback<Optional<String>> callback) {
    SignalExecutors.UNBOUNDED.execute(() -> {
      Log.blockUntilAllWritesFinished();
      LogDatabase.getInstance(context).trimToSize();
      callback.onResult(submitLogInternal(System.currentTimeMillis(), getPrefixLogLinesInternal(), Tracer.getInstance().serialize()));
    });
  }

  /**
   * Submits a log with the provided prefix lines.
   *
   * @param untilTime Only submit logs from {@link LogDatabase} if they were created before this time. This is our way of making sure that the logs we submit
   *                  only include the logs that we've already shown the user. It's possible some old logs may have been trimmed off in the meantime, but no
   *                  new ones could pop up.
   */
  public void submitLogWithPrefixLines(long untilTime, @NonNull List<LogLine> prefixLines, @Nullable byte[] trace, Callback<Optional<String>> callback) {
    SignalExecutors.UNBOUNDED.execute(() -> callback.onResult(submitLogInternal(untilTime, prefixLines, trace)));
  }

  public void writeLogToDisk(@NonNull Uri uri, long untilTime, Callback<Boolean> callback) {
    SignalExecutors.UNBOUNDED.execute(() -> {
      try (ZipOutputStream outputStream = new ZipOutputStream(context.getContentResolver().openOutputStream(uri))) {
        StringBuilder prefixLines = linesToStringBuilder(getPrefixLogLinesInternal(), null);

        outputStream.putNextEntry(new ZipEntry("log.txt"));
        outputStream.write(prefixLines.toString().getBytes(StandardCharsets.UTF_8));

        try (LogDatabase.Reader reader = LogDatabase.getInstance(context).getAllBeforeTime(untilTime)) {
          while (reader.hasNext()) {
            outputStream.write(reader.next().getBytes());
            outputStream.write("\n".getBytes());
          }
        } catch (IllegalStateException e) {
          Log.e(TAG, "Failed to read row!", e);
          callback.onResult(false);
          return;
        }

        outputStream.closeEntry();

        outputStream.putNextEntry(new ZipEntry("signal.trace"));
        outputStream.write(Tracer.getInstance().serialize());
        outputStream.closeEntry();

        callback.onResult(true);
      } catch (IOException e) {
        callback.onResult(false);
      }
    });
  }

  @WorkerThread
  private @NonNull Optional<String> submitLogInternal(long untilTime, @NonNull List<LogLine> prefixLines, @Nullable byte[] trace) {
    String traceUrl = null;
    if (trace != null) {
      try {
        traceUrl = uploadContent("application/octet-stream", RequestBody.create(MediaType.get("application/octet-stream"), trace));
      } catch (IOException e) {
        Log.w(TAG, "Error during trace upload.", e);
        return Optional.empty();
      }
    }

    StringBuilder prefixStringBuilder = linesToStringBuilder(prefixLines, traceUrl);

    try {
      Stopwatch stopwatch = new Stopwatch("log-upload");

      ParcelFileDescriptor[] fds     = ParcelFileDescriptor.createPipe();
      Uri                    gzipUri = BlobProvider.getInstance()
                                                   .forData(new ParcelFileDescriptor.AutoCloseInputStream(fds[0]), 0)
                                                   .withMimeType("application/gzip")
                                                   .createForSingleSessionOnDiskAsync(context, null, null);

      OutputStream gzipOutput = new GZIPOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(fds[1]));

      gzipOutput.write(prefixStringBuilder.toString().getBytes());

      stopwatch.split("front-matter");

      try (LogDatabase.Reader reader = LogDatabase.getInstance(context).getAllBeforeTime(untilTime)) {
        while (reader.hasNext()) {
          gzipOutput.write(reader.next().getBytes());
          gzipOutput.write("\n".getBytes());
        }
      } catch (IllegalStateException e) {
        Log.e(TAG, "Failed to read row!", e);
        return Optional.empty();
      }

      StreamUtil.close(gzipOutput);

      stopwatch.split("body");

      String logUrl = uploadContent("application/gzip", new RequestBody() {
        @Override
        public @NonNull MediaType contentType() {
          return MediaType.get("application/gzip");
        }

        @Override public long contentLength() {
          return BlobProvider.getInstance().calculateFileSize(context, gzipUri);
        }

        @Override
        public void writeTo(@NonNull BufferedSink sink) throws IOException {
          Source source = Okio.source(BlobProvider.getInstance().getStream(context, gzipUri));
          sink.writeAll(source);
        }
      });

      stopwatch.split("upload");
      stopwatch.stop(TAG);

      BlobProvider.getInstance().delete(context, gzipUri);

      return Optional.of(logUrl);
    } catch (IOException e) {
      Log.w(TAG, "Error during log upload.", e);
      return Optional.empty();
    }
  }

  @WorkerThread
  private @NonNull String uploadContent(@NonNull String contentType, @NonNull RequestBody requestBody) throws IOException {
    OkHttpClient client = new OkHttpClient.Builder().addInterceptor(new StandardUserAgentInterceptor()).dns(SignalServiceNetworkAccess.DNS).build();

    try (Response response = client.newCall(new Request.Builder().url(API_ENDPOINT).get().build()).execute()) {
      ResponseBody body = response.body();

      if (!response.isSuccessful() || body == null) {
        throw new IOException("Unsuccessful response: " + response);
      }

      JSONObject            json   = new JSONObject(body.string());
      String                url    = json.getString("url");
      JSONObject            fields = json.getJSONObject("fields");
      String                item   = fields.getString("key");
      MultipartBody.Builder post   = new MultipartBody.Builder();
      Iterator<String>      keys   = fields.keys();

      post.addFormDataPart("Content-Type", contentType);

      while (keys.hasNext()) {
        String key = keys.next();
        post.addFormDataPart(key, fields.getString(key));
      }

      post.addFormDataPart("file", "file", requestBody);

      try (Response postResponse = client.newCall(new Request.Builder().url(url).post(post.build()).build()).execute()) {
        if (!postResponse.isSuccessful()) {
          throw new IOException("Bad response: " + postResponse);
        }
      }

      return API_ENDPOINT + "/" + item;
    } catch (JSONException e) {
      Log.w(TAG, "Error during upload.", e);
      throw new IOException(e);
    }
  }

  @WorkerThread
  private @NonNull List<LogLine> getPrefixLogLinesInternal() {
    long startTime = System.currentTimeMillis();

    int maxTitleLength = Stream.of(SECTIONS).reduce(0, (max, section) -> Math.max(max, section.getTitle().length()));

    List<LogLine> allLines = new ArrayList<>();

    for (LogSection section : SECTIONS) {
      List<LogLine> lines = getLinesForSection(context, section, maxTitleLength);

      if (SECTIONS.indexOf(section) != SECTIONS.size() - 1) {
        for (int i = 0; i < SECTION_SPACING; i++) {
          lines.add(SimpleLogLine.EMPTY);
        }
      }

      allLines.addAll(lines);
    }

    List<LogLine> withIds = new ArrayList<>(allLines.size());

    for (int i = 0; i < allLines.size(); i++) {
      withIds.add(new CompleteLogLine(i, allLines.get(i)));
    }

    Log.d(TAG, "Total time: " + (System.currentTimeMillis() - startTime) + " ms");

    return withIds;
  }

  @WorkerThread
  private static @NonNull List<LogLine> getLinesForSection(@NonNull Context context, @NonNull LogSection section, int maxTitleLength) {
    long startTime = System.currentTimeMillis();

    List<LogLine> out = new ArrayList<>();
    out.add(new SimpleLogLine(formatTitle(section.getTitle(), maxTitleLength), LogLine.Style.NONE, LogLine.Placeholder.NONE));

    if (section.hasContent()) {
      CharSequence content = Scrubber.scrub(section.getContent(context));

      List<LogLine> lines = Stream.of(Pattern.compile("\\n").split(content))
                                  .map(s -> new SimpleLogLine(s, LogStyleParser.parseStyle(s), LogStyleParser.parsePlaceholderType(s)))
                                  .map(line -> (LogLine) line)
                                  .toList();

      out.addAll(lines);
    }

    Log.d(TAG, "[" + section.getTitle() + "] Took " + (System.currentTimeMillis() - startTime) + " ms");

    return out;
  }

  private static @NonNull String formatTitle(@NonNull String title, int maxTitleLength) {
    int neededPadding = maxTitleLength - title.length();
    int leftPadding   = neededPadding  / 2;
    int rightPadding  = neededPadding  - leftPadding;

    StringBuilder out = new StringBuilder();

    for (int i = 0; i < leftPadding + MIN_DECORATIONS; i++) {
      out.append(TITLE_DECORATION);
    }

    out.append(' ').append(title).append(' ');

    for (int i = 0; i < rightPadding + MIN_DECORATIONS; i++) {
      out.append(TITLE_DECORATION);
    }

    return out.toString();
  }

  private static @NonNull StringBuilder linesToStringBuilder(@NonNull List<LogLine> lines, @Nullable String traceUrl) {
    StringBuilder stringBuilder = new StringBuilder();
    for (LogLine line : lines) {
      switch (line.getPlaceholderType()) {
        case NONE:
          stringBuilder.append(line.getText()).append('\n');
          break;
        case TRACE:
          stringBuilder.append(traceUrl).append('\n');
          break;
      }
    }

    return stringBuilder;
  }

  public interface Callback<E> {
    void onResult(E result);
  }
}
