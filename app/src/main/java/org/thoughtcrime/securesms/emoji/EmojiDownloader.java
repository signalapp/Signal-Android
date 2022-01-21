package org.thoughtcrime.securesms.emoji;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import com.mobilecoin.lib.util.Hex;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Okio;
import okio.Sink;
import okio.Source;

/**
 * Helper for downloading Emoji files via {@link EmojiRemote}.
 */
public class EmojiDownloader {

  public static @NonNull EmojiFiles.Name downloadAndVerifyJsonFromRemote(@NonNull Context context, @NonNull EmojiFiles.Version version) throws IOException {
    return downloadAndVerifyFromRemote(context,
                                       version,
                                       () -> EmojiRemote.getObject(new EmojiJsonRequest(version.getVersion())),
                                       EmojiFiles.Name::forEmojiDataJson);
  }

  public static @NonNull EmojiFiles.Name downloadAndVerifyImageFromRemote(@NonNull Context context,
                                                                          @NonNull EmojiFiles.Version version,
                                                                          @NonNull String bucket,
                                                                          @NonNull String imagePath,
                                                                          @NonNull String format) throws IOException
  {
    return downloadAndVerifyFromRemote(context,
                                       version,
                                       () -> EmojiRemote.getObject(new EmojiImageRequest(version.getVersion(), bucket, imagePath, format)),
                                       () -> new EmojiFiles.Name(imagePath, UUID.randomUUID()));
  }

  public static void streamFileFromRemote(@NonNull EmojiFiles.Version version,
                                          @NonNull String bucket,
                                          @NonNull String path,
                                          @NonNull Consumer<InputStream> streamConsumer)
      throws IOException
  {
    streamFromRemote(() -> EmojiRemote.getObject(new EmojiFileRequest(version.getVersion(), bucket, path)),
                     streamConsumer);
  }

  private static @NonNull EmojiFiles.Name downloadAndVerifyFromRemote(@NonNull Context context,
                                                                      @NonNull EmojiFiles.Version version,
                                                                      @NonNull Producer<Response> responseProducer,
                                                                      @NonNull Producer<EmojiFiles.Name> nameProducer) throws IOException
  {
    try (Response response = responseProducer.produce()) {
      if (!response.isSuccessful()) {
        throw new IOException("Unsuccessful response " + response.code());
      }

      ResponseBody responseBody = response.body();
      if (responseBody == null) {
        throw new IOException("No response body");
      }

      String responseMD5 = getMD5FromResponse(response);
      if (responseMD5 == null) {
        throw new IOException("Invalid ETag on response");
      }

      EmojiFiles.Name name = nameProducer.produce();

      byte[] savedMd5;

      try (OutputStream outputStream = EmojiFiles.openForWriting(context, version, name.getUuid())) {
        Source source = response.body().source();
        Sink   sink   = Okio.sink(outputStream);

        Okio.buffer(source).readAll(sink);
        outputStream.flush();

        source.close();
        sink.close();

        savedMd5 = EmojiFiles.getMd5(context, version, name.getUuid());
      }

      if (!Arrays.equals(savedMd5, Hex.toByteArray(responseMD5))) {
        EmojiFiles.delete(context, version, name.getUuid());
        throw new IOException("MD5 Mismatch.");
      }

      return name;
    }
  }

  private static void streamFromRemote(@NonNull Producer<Response> responseProducer,
                                       @NonNull Consumer<InputStream> streamConsumer) throws IOException
  {
    try (Response response = responseProducer.produce()) {
      if (!response.isSuccessful()) {
        throw new IOException("Unsuccessful response " + response.code());
      }

      ResponseBody responseBody = response.body();
      if (responseBody == null) {
        throw new IOException("No response body");
      }

      streamConsumer.accept(Okio.buffer(responseBody.source()).inputStream());
    }
  }

  private static @Nullable String getMD5FromResponse(@NonNull Response response) {
    Pattern pattern = Pattern.compile(".*([a-f0-9]{32}).*");
    String  header  = response.header("etag");
    Matcher matcher = pattern.matcher(header);

    if (matcher.find()) {
      return matcher.group(1);
    } else {
      return null;
    }
  }

  private interface Producer<T> {
    @NonNull T produce();
  }
}
