package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.mms.TextSlide;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.whispersystems.libsignal.util.guava.Optional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MessageUtil {

  private MessageUtil() {}

  /**
   * @return If the message is longer than the allowed text size, this will return trimmed text with
   *         an accompanying TextSlide. Otherwise it'll just return the original text.
   */
  public static SplitResult getSplitMessage(@NonNull Context context, @NonNull String rawText, int maxPrimaryMessageSize) {
    String              bodyText  = rawText;
    Optional<TextSlide> textSlide = Optional.absent();

    if (bodyText.length() > maxPrimaryMessageSize) {
      bodyText = rawText.substring(0, maxPrimaryMessageSize);

      byte[] textData  = rawText.getBytes();
      String timestamp = new SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(new Date());
      String filename  = String.format("signal-%s.txt", timestamp);
      Uri    textUri   = BlobProvider.getInstance()
                                     .forData(textData)
                                     .withMimeType(MediaUtil.LONG_TEXT)
                                     .withFileName(filename)
                                     .createForSingleSessionInMemory();

      textSlide = Optional.of(new TextSlide(context, textUri, filename, textData.length));
    }

    return new SplitResult(bodyText, textSlide);
  }

  public static class SplitResult {
    private final String              body;
    private final Optional<TextSlide> textSlide;

    private SplitResult(@NonNull String body, @NonNull Optional<TextSlide> textSlide) {
      this.body      = body;
      this.textSlide = textSlide;
    }

    public @NonNull String getBody() {
      return body;
    }

    public @NonNull Optional<TextSlide> getTextSlide() {
      return textSlide;
    }
  }
}
