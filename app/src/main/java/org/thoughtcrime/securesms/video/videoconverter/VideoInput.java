package org.thoughtcrime.securesms.video.videoconverter;

import android.content.Context;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

public abstract class VideoInput implements Closeable {

  @NonNull
  abstract MediaExtractor createExtractor() throws IOException;

  public static class FileVideoInput extends VideoInput {

    final File file;

    public FileVideoInput(final @NonNull File file) {
      this.file = file;
    }

    @Override
    public @NonNull MediaExtractor createExtractor() throws IOException {
      final MediaExtractor extractor = new MediaExtractor();
      extractor.setDataSource(file.getAbsolutePath());
      return extractor;
    }

    @Override
    public void close() {
    }
  }

  public static class UriVideoInput extends VideoInput {

    final Uri uri;
    final Context context;

    public UriVideoInput(final @NonNull Context context, final @NonNull Uri uri) {
      this.uri = uri;
      this.context = context;
    }

    @Override
    public @NonNull MediaExtractor createExtractor() throws IOException {
      final MediaExtractor extractor = new MediaExtractor();
      extractor.setDataSource(context, uri, null);
      return extractor;
    }

    @Override
    public void close() {
    }
  }

  @RequiresApi(23)
  public static class MediaDataSourceVideoInput extends VideoInput {

    private final MediaDataSource mediaDataSource;

    public MediaDataSourceVideoInput(final @NonNull MediaDataSource mediaDataSource) {
      this.mediaDataSource = mediaDataSource;
    }

    @Override
    public @NonNull MediaExtractor createExtractor() throws IOException {
      final MediaExtractor extractor = new MediaExtractor();
      extractor.setDataSource(mediaDataSource);
      return extractor;
    }

    @Override
    public void close() throws IOException {
      mediaDataSource.close();
    }
  }
}
